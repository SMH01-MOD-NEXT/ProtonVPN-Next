#!/usr/bin/env python3
"""Exercise cache validation without building Go or modifying the installed AAR."""
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/build-awgbox-lib.sh"
FUNCTIONS = SCRIPT.read_text().split('if [[ "$FORCE_REBUILD" != "1" ]] && artifact_is_valid;', 1)[0]


class AwgBoxBuildCacheTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.recipe_root = Path(self.temp.name) / "recipe"
        for name in ("scripts/build-awgbox-lib.sh", "scripts/patches/awgbox-tor-android.patch",
                     "scripts/patches/awgbox-tor-log-noise.patch"):
            target = self.recipe_root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, target)
        self.output = Path(self.temp.name) / "libbox.aar"
        with ZipFile(self.output, "w") as archive:
            for name in ("AndroidManifest.xml", "classes.jar", "jni/arm64-v8a/libbox.so"):
                archive.writestr(name, b"test")

    def run_shell(self, commands):
        # Native ELF validation is separate from the cache identity under test.
        shell = FUNCTIONS + f'\nROOT={shlex.quote(str(self.recipe_root))}\nOUTPUT={shlex.quote(str(self.output))}\n'
        shell += 'native_output_is_valid() { return 0; }\n' + commands
        return subprocess.run(["bash", "-c", shell], capture_output=True, text=True)

    def assert_cache(self, commands, valid):
        result = self.run_shell(commands + '\nartifact_is_valid\n')
        self.assertEqual(result.returncode == 0, valid, result.stderr)

    STAMP = 'printf "%s %s\\n" "$(build_fingerprint)" "$(sha256sum "$OUTPUT" | awk \'{ print $1 }\')" > "$OUTPUT.build-info"\n'

    def test_legacy_aar_without_fingerprint_is_rejected(self):
        self.assert_cache('', False)

    def test_matching_artifact_is_reused(self):
        self.assert_cache(self.STAMP, True)

    def test_changed_android_patch_is_rejected(self):
        self.assert_cache(self.STAMP + 'printf changed >> "$ROOT/scripts/patches/awgbox-tor-android.patch"', False)

    def test_changed_log_patch_is_rejected(self):
        self.assert_cache(self.STAMP + 'printf changed >> "$ROOT/scripts/patches/awgbox-tor-log-noise.patch"', False)

    def test_changed_build_script_is_rejected(self):
        self.assert_cache(self.STAMP + 'printf changed >> "$ROOT/scripts/build-awgbox-lib.sh"', False)

    def test_changed_revision_is_rejected(self):
        self.assert_cache(self.STAMP + 'EXPECTED_COMMIT=another-revision', False)

    def test_changed_toolchain_is_rejected(self):
        self.assert_cache(self.STAMP + 'GO_TOOLCHAIN=another-toolchain', False)

    def test_changed_repository_is_rejected(self):
        self.assert_cache(self.STAMP + 'REPO=https://example.invalid/fork.git', False)

    def test_modified_aar_is_rejected(self):
        self.assert_cache(self.STAMP + 'printf modified >> "$OUTPUT"', False)

    def test_missing_aar_is_rejected(self):
        self.assert_cache(self.STAMP + 'rm "$OUTPUT"', False)

    def test_malformed_metadata_is_rejected(self):
        self.assert_cache('printf malformed > "$OUTPUT.build-info"', False)

    def test_corrupt_archive_is_rejected(self):
        self.assert_cache('printf corrupt > "$OUTPUT"\n' + self.STAMP, False)


if __name__ == "__main__":
    unittest.main()
