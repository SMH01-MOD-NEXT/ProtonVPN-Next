import os
import subprocess
import google.generativeai as genai

# Configuration from environment variables
GEMINI_API_KEY = os.environ.get('GEMINI_API_KEY')
GEMINI_MODEL = os.environ.get('GEMINI_MODEL', 'gemini-3.1-flash-lite')
TAG_NAME = os.environ.get('CI_COMMIT_TAG', 'Unknown')
REPO_NAME = os.environ.get('CI_REPO', 'ProtonVPN-Next')

def run_command(cmd):
    """Runs a shell command and returns the output."""
    try:
        result = subprocess.check_output(cmd, stderr=subprocess.STDOUT, text=True)
        return result.strip()
    except subprocess.CalledProcessError as e:
        print(f"Error running command {' '.join(cmd)}: {e.output}")
        return None

def get_previous_tag(current_tag):
    """Finds the tag before the current one."""
    # We try to get the tag attached to the parent of the current commit
    # This works best if the tag is on a merge commit or the tip of a branch
    tag = run_command(['git', 'describe', '--tags', '--abbrev=0', f'{current_tag}^'])
    if not tag:
        # Fallback: just get the latest tag that isn't the current one
        tags = run_command(['git', 'tag', '--sort=-v:refname'])
        if tags:
            tag_list = tags.split('\n')
            for t in tag_list:
                if t != current_tag:
                    return t
    return tag

def get_commit_history(current_tag, prev_tag):
    """Gets the commit history between two tags."""
    if prev_tag:
        print(f"Fetching history between {prev_tag} and {current_tag}...")
        range_spec = f"{prev_tag}..{current_tag}"
    else:
        print(f"No previous tag found. Fetching all history up to {current_tag}...")
        range_spec = current_tag

    return run_command(['git', 'log', range_spec, '--pretty=format:- %s (%h)'])

def generate_changelog(history):
    """Calls Gemini to generate the changelog."""
    if not GEMINI_API_KEY:
        print("Error: GEMINI_API_KEY is not set.")
        return "Manual changelog needed: GEMINI_API_KEY missing."

    genai.configure(api_key=GEMINI_API_KEY)
    model = genai.GenerativeModel(GEMINI_MODEL)

    prompt_template = os.environ.get('CHANGELOG_PROMPT')
    if not prompt_template:
        print("Error: CHANGELOG_PROMPT is not set.")
        return f"AI Generation Failed: CHANGELOG_PROMPT missing. Raw history:\n\n{history}"[:1000]

    # Replace placeholders if they exist in the custom prompt
    full_prompt = prompt_template
    for placeholder in ['[TAG_NAME]', '[VERSION]', '[VERSION_NAME]', '[TAG]']:
        full_prompt = full_prompt.replace(placeholder, TAG_NAME)

    # Append history
    full_prompt += f"\n\nInput commit history:\n{history}"

    try:
        response = model.generate_content(full_prompt)
        text = response.text.strip()

        # Final safety replacement in the generated text if Gemini kept placeholders
        for placeholder in ['[TAG_NAME]', '[VERSION]', '[VERSION_NAME]', '[TAG]']:
            text = text.replace(placeholder, TAG_NAME)

        # Enforce 1000 character limit and truncation for Telegram compatibility
        if len(text) > 1000:
            text = text[:997] + "..."

        return text
    except Exception as e:
        print(f"Error calling Gemini API: {e}")
        return f"AI Generation Failed. Raw history:\n\n{history}"[:1000]

def main():
    print(f"Generating AI changelog for {TAG_NAME}...")

    if TAG_NAME == 'Unknown':
        print("Warning: CI_COMMIT_TAG is not set. Using HEAD.")
        current_tag = 'HEAD'
    else:
        current_tag = TAG_NAME

    prev_tag = get_previous_tag(current_tag)
    history = get_commit_history(current_tag, prev_tag)

    if not history:
        print("No commits found to generate changelog.")
        changelog = f"## 📦 Release {TAG_NAME}\nNo changes recorded."
    else:
        changelog = generate_changelog(history)

    # Write to changelog.txt for Woodpecker release plugin
    with open('changelog.txt', 'w', encoding='utf-8') as f:
        f.write(changelog)

    print("Changelog generated and saved to changelog.txt")
    print("-" * 20)
    print(changelog)
    print("-" * 20)

if __name__ == "__main__":
    main()
