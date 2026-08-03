import os
import sys
import json
import glob
import subprocess
import boto3
from botocore.client import Config

# R2 Configuration from secrets
R2_ACCESS_KEY = os.environ.get('R2_ACCESS_KEY')
R2_SECRET_KEY = os.environ.get('R2_SECRET_KEY')
R2_ENDPOINT = os.environ.get('R2_ENDPOINT')
R2_BUCKET = os.environ.get('R2_BUCKET')
R2_PUBLIC_URL = os.environ.get('R2_PUBLIC_URL', 'https://pub-xxxx.r2.dev')

# CI environment (GitLab)
EVENT = os.environ.get('CI_PIPELINE_SOURCE', 'push')
TAG = os.environ.get('CI_COMMIT_TAG')
COMMIT_SHA = os.environ.get('CI_COMMIT_SHA', 'unknown')[:8]
# A write-enabled token is required: CI_JOB_TOKEN can read the repository but is
# rejected by GitLab on `git push`, which silently froze the website repository.
PUSH_TOKEN = (
    os.environ.get('GITLAB_TOKEN')
    or os.environ.get('WEBSITE_PUSH_TOKEN')
    or os.environ.get('CI_PUSH_TOKEN')
)
GITHUB_TOKEN = os.environ.get('GITHUB_TOKEN')

# The website is a separate repository, deployed to Deno Deploy and Cloudflare.
# It used to be the `website` branch of this repository, but Deno Deploy builds
# every branch of a linked repository, so the site had to move out.
WEBSITE_BRANCH = os.environ.get('WEBSITE_BRANCH', 'main')
WEBSITE_REPO = os.environ.get('WEBSITE_REPO', 'vpn-next-group/ProtonVPN-Next-WEB')
GITLAB_HOST = os.environ.get('CI_SERVER_HOST', 'gitlab.com')
MIRROR_REPO = os.environ.get('MIRROR_REPO', 'SMH01-MIRRORS/ProtonVPN-Next-WEB')
CHANNELS = ("stable", "nightly")
# Both product flavors are published. The in-app updater only understands the
# legacy "release"/"debug" keys, so the standard flavor keeps them and privacy
# gets its own keys, which older clients simply ignore.
FLAVORS = ("standard", "privacy")
VERSION_CODE_BASE = 605159512


def metadata_key(flavor, build_type):
    """The update.json key for a flavor/build type pair (see the website matrix)."""
    if flavor == "standard":
        return build_type
    return f"{flavor}{build_type.capitalize()}"


def fail(message):
    print(f"❌ {message}")
    sys.exit(1)


def run(command, cwd=None, check=True, capture=False):
    """Runs a shell command; by default a failure aborts the job instead of being ignored."""
    result = subprocess.run(
        command, shell=True, cwd=cwd, text=True,
        capture_output=capture,
    )
    if capture and result.stdout:
        print(result.stdout.strip())
    if check and result.returncode != 0:
        details = (result.stderr or "").strip()
        fail(f"Command failed ({result.returncode}): {command}\n{mask_secrets(details)}")
    return result


def mask_secrets(text):
    for secret in (PUSH_TOKEN, GITHUB_TOKEN):
        if secret:
            text = text.replace(secret, "***")
    return text


def get_git_output(command, cwd=None):
    try:
        return subprocess.check_output(
            command, shell=True, cwd=cwd, stderr=subprocess.DEVNULL
        ).decode().strip()
    except subprocess.CalledProcessError:
        return ""


def ensure_full_history():
    """CI clones are shallow by default; the version code is derived from the commit count."""
    if get_git_output("git rev-parse --is-shallow-repository") == "true":
        print("Repository is shallow, fetching full history...")
        run("git fetch --unshallow --tags", check=False)
    run("git fetch --tags --force", check=False)

    if get_git_output("git rev-parse --is-shallow-repository") == "true":
        fail(
            "Repository is still shallow. Set GIT_DEPTH: 0 for this job, otherwise the "
            "published versionCode goes backwards and clients never see the update."
        )


def upload_to_r2(file_path, target_dir):
    print(f"Uploading {file_path} to R2 folder {target_dir}...")
    s3 = boto3.resource('s3',
        endpoint_url=R2_ENDPOINT,
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        config=Config(signature_version='s3v4'),
        region_name='auto'
    )
    bucket = s3.Bucket(R2_BUCKET)

    file_name = os.path.basename(file_path)
    key = f"{target_dir}/{file_name}"
    bucket.upload_file(file_path, key)

    return f"{R2_PUBLIC_URL}/{key}"


def clear_r2_dir(target_dir):
    print(f"Cleaning R2 directory: {target_dir}")
    s3 = boto3.resource('s3',
        endpoint_url=R2_ENDPOINT,
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        config=Config(signature_version='s3v4'),
        region_name='auto'
    )
    bucket = s3.Bucket(R2_BUCKET)
    bucket.objects.filter(Prefix=f"{target_dir}/").delete()


def authenticated_repo_url():
    """The website repository on GitLab, not this one.

    The token therefore has to be scoped to a different project than the
    pipeline runs in; a project access token limited to this repository will
    clone but fail on push.
    """
    if not PUSH_TOKEN:
        fail(
            "No write-enabled token found. Set GITLAB_TOKEN (an access token with "
            f"'write_repository' on {WEBSITE_REPO}) in the CI/CD variables: "
            "CI_JOB_TOKEN cannot push, and a token scoped to this repository "
            "cannot push to the website repository."
        )

    return f"https://oauth2:{PUSH_TOKEN}@{GITLAB_HOST}/{WEBSITE_REPO}.git"


def mirror_repo_url():
    if GITHUB_TOKEN:
        return f"https://git:{GITHUB_TOKEN}@github.com/{MIRROR_REPO}.git"
    return "https://github.com/" + MIRROR_REPO + ".git"


def load_json(path):
    if not os.path.exists(path):
        return {}
    try:
        with open(path, 'r') as handle:
            return json.load(handle)
    except (ValueError, OSError):
        return {}


def newest_entries(*sources):
    """Merges OTA metadata, keeping the highest versionCode per channel and build type.

    The mirror can hold builds that never reached GitLab (and vice versa); rebuilding the
    file from a single side is what wiped the stable channel on GitHub.
    """
    merged = {}
    for source in sources:
        for channel, builds in (source or {}).items():
            if channel not in CHANNELS or not isinstance(builds, dict):
                continue
            for build_type, entry in builds.items():
                if not isinstance(entry, dict) or "url" not in entry:
                    continue
                current = merged.setdefault(channel, {}).get(build_type)
                if current is None or int(entry.get("versionCode", 0)) >= int(current.get("versionCode", 0)):
                    merged[channel][build_type] = entry
    return merged


def read_mirror_metadata(repo_dir, json_relative_path):
    """Reads update.json from the GitHub mirror so its metadata is never dropped."""
    fetch = run(f"git fetch mirror {WEBSITE_BRANCH}", cwd=repo_dir, check=False, capture=True)
    if fetch.returncode != 0:
        print("⚠️ Could not fetch the GitHub mirror, continuing with GitLab metadata only.")
        return {}

    content = get_git_output(f"git show FETCH_HEAD:{json_relative_path}", cwd=repo_dir)
    if not content:
        return {}
    try:
        return json.loads(content)
    except ValueError:
        return {}


def push_website(repo_dir, channel):
    status = get_git_output("git status --porcelain", cwd=repo_dir)
    if not status:
        print("No metadata changes to publish.")
        return False

    run("git config user.email 'ci@protonmod.next'", cwd=repo_dir)
    run("git config user.name 'CI Bot'", cwd=repo_dir)
    run("git add public/update.json", cwd=repo_dir)
    if os.path.exists(os.path.join(repo_dir, "dist", "update.json")):
        run("git add dist/update.json", cwd=repo_dir)
    run(f"git commit -m 'chore: update ota metadata for {channel} channel'", cwd=repo_dir)

    for attempt in range(3):
        push = run(f"git push origin HEAD:{WEBSITE_BRANCH}", cwd=repo_dir, check=False, capture=True)
        if push.returncode == 0:
            print("✅ Pushed OTA metadata to GitLab.")
            return True
        print(mask_secrets((push.stderr or "").strip()))
        if attempt < 2:
            print("Push rejected, rebasing on the latest website branch and retrying...")
            run(f"git pull --rebase origin {WEBSITE_BRANCH}", cwd=repo_dir, check=False)

    fail(
        "Could not push OTA metadata to GitLab. Verify that GITLAB_TOKEN is a project "
        "access token with 'write_repository' on the website repository and that its "
        "default branch is not protected "
        "against it."
    )


def push_mirror(repo_dir):
    if not GITHUB_TOKEN:
        print("⚠️ GITHUB_TOKEN not set, skipping the GitHub mirror push.")
        return

    # Only runs after GitLab accepted the commit, so GitLab stays the source of truth.
    result = run(f"git push mirror HEAD:{WEBSITE_BRANCH}", cwd=repo_dir, check=False, capture=True)
    if result.returncode == 0:
        print("✅ Pushed OTA metadata to the GitHub mirror.")
        return

    print(mask_secrets((result.stderr or "").strip()))
    print("Mirror rejected the fast-forward push, forcing GitLab state onto the mirror...")
    forced = run(
        f"git push mirror HEAD:{WEBSITE_BRANCH} --force", cwd=repo_dir, check=False, capture=True
    )
    if forced.returncode == 0:
        print("✅ Mirror synchronised with GitLab.")
    else:
        print(mask_secrets((forced.stderr or "").strip()))
        fail("Failed to push to the GitHub mirror. Check GITHUB_TOKEN 'Contents: Read and Write'.")


def main():
    if not all([R2_ACCESS_KEY, R2_SECRET_KEY, R2_ENDPOINT, R2_BUCKET]):
        fail("Missing R2 configuration secrets!")

    is_tag = TAG is not None
    channel = "stable" if is_tag else "nightly"
    target_dir = "VPN-Next" if is_tag else "VPN-Next-TEST"
    build_types = ["release"] if is_tag else ["debug", "release"]

    ensure_full_history()

    # 1. Collect the APKs before touching R2, so a bad build never empties the bucket.
    #    Every flavor/build type combination the pipeline produced is published, so the
    #    website can offer the full matrix instead of a single standard build.
    staged = []
    for flavor in FLAVORS:
        for build_type in build_types:
            gradle_flavor = f"{channel}{flavor.capitalize()}"
            apk_pattern = f"app/build/outputs/apk/{gradle_flavor}/{build_type}/*.apk"
            apk_files = glob.glob(apk_pattern)
            if not apk_files:
                fail(f"No APK found for {gradle_flavor}/{build_type} at {apk_pattern}")
            staged.append((metadata_key(flavor, build_type), apk_files[0]))

    # 2. Prepare metadata
    commit_count = int(get_git_output("git rev-list --count HEAD") or "0")
    if commit_count == 0:
        fail("Could not determine the commit count, refusing to publish a broken versionCode.")
    version_code = VERSION_CODE_BASE + commit_count
    version_name_base = TAG if is_tag else get_git_output("git describe --tags --always")
    changelog = get_git_output("git log -1 --pretty=%B")
    print(f"Publishing {channel}: versionCode={version_code}, versionName={version_name_base}")

    # 3. Update the website JSON, starting from the newest metadata of both remotes.
    print(f"Updating update.json in {WEBSITE_REPO} ({WEBSITE_BRANCH})...")
    run("rm -rf website_repo", check=False)
    run(f"git clone --branch {WEBSITE_BRANCH} {authenticated_repo_url()} website_repo")
    run(f"git remote add mirror {mirror_repo_url()}", cwd="website_repo", check=False)

    json_relative_path = "public/update.json"
    json_path = os.path.join("website_repo", json_relative_path)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)

    data = newest_entries(load_json(json_path), read_mirror_metadata("website_repo", json_relative_path))

    published = data.get(channel, {})
    for variant, _ in staged:
        previous = published.get(variant, {})
        if int(previous.get("versionCode", 0)) > version_code:
            fail(
                f"Refusing to publish {channel}/{variant}: versionCode {version_code} is lower "
                f"than the published {previous.get('versionCode')}. This means the CI history is "
                "incomplete."
            )

    # 4. Upload the APKs
    clear_r2_dir(target_dir)
    version_name = version_name_base + ("" if is_tag else "-nightly")
    channel_data = {}
    for build_type, apk_path in staged:
        channel_data[build_type] = {
            "versionCode": int(version_code),
            "versionName": version_name,
            "url": upload_to_r2(apk_path, target_dir),
            "changelog": changelog,
            "force": False,
        }

    # Replace only the current channel; the other channel keeps its published build.
    data[channel] = channel_data

    # The website is a Vite build served from dist/, and public/update.json is only
    # copied into it at build time. Writing both keeps the deployed file in sync
    # without rebuilding the site on every OTA publish.
    for relative_path in (json_relative_path, "dist/update.json"):
        target = os.path.join("website_repo", relative_path)
        if relative_path != json_relative_path and not os.path.isdir(os.path.dirname(target)):
            continue
        with open(target, 'w') as handle:
            json.dump(data, handle, indent=2)
            handle.write("\n")

    if push_website("website_repo", channel):
        push_mirror("website_repo")


if __name__ == '__main__':
    main()
