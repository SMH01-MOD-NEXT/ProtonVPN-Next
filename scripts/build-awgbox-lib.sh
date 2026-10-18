#!/usr/bin/env bash
set -euo pipefail

REF="${AWGBOX_REF:-1.15.0-alpha.12-awgm.33}"
EXPECTED_COMMIT="${AWGBOX_COMMIT:-2015b9b2e64ab0cf3321a1e5dfddb3bca732f82f}"
GO_TOOLCHAIN="${AWGBOX_GO_TOOLCHAIN:-go1.25.5}"
REPO="${AWGBOX_REPO:-https://github.com/hoaxisr/amnezia-box.git}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${AWGBOX_WORK_DIR:-$ROOT/.artifacts/amnezia-box}"
GOPATH_DIR="${AWGBOX_GOPATH_DIR:-$ROOT/.artifacts/awgbox-gopath}"
OUTPUT="$ROOT/app/libs/libbox-awgbox.aar"
FORCE_REBUILD="${AWGBOX_FORCE_REBUILD:-0}"

output_is_valid() {
  [[ -s "$OUTPUT" ]] || return 1
  command -v python3 >/dev/null || return 1
  python3 - "$OUTPUT" <<'PY_AAR'
from pathlib import Path
from zipfile import BadZipFile, ZipFile
import sys

path = Path(sys.argv[1])
required = {"AndroidManifest.xml", "classes.jar", "jni/arm64-v8a/libbox.so"}
try:
    with ZipFile(path) as archive:
        names = set(archive.namelist())
        if not required.issubset(names) or archive.testzip() is not None:
            raise SystemExit(1)
except (BadZipFile, OSError):
    raise SystemExit(1)
PY_AAR
}

native_output_is_valid() {
  local elf_reader="${AWGBOX_READELF:-}"
  if [[ -z "$elf_reader" ]]; then
    elf_reader="$(command -v llvm-readelf || command -v readelf || true)"
  fi
  if [[ -z "$elf_reader" && -n "${ANDROID_HOME:-}" ]]; then
    elf_reader="$(find "$ANDROID_HOME/ndk" -path '*/toolchains/llvm/prebuilt/*/bin/llvm-readelf' -type f -print -quit 2>/dev/null || true)"
  fi
  if [[ -z "$elf_reader" ]]; then
    echo "llvm-readelf or readelf is required to validate the AWGBox native library" >&2
    return 1
  fi

  local temp_so unresolved_go_symbols
  temp_so="$(mktemp)"
  if ! python3 - "$OUTPUT" "$temp_so" <<'PY_SO'
from pathlib import Path
from zipfile import ZipFile
import sys

with ZipFile(sys.argv[1]) as archive:
    Path(sys.argv[2]).write_bytes(archive.read("jni/arm64-v8a/libbox.so"))
PY_SO
  then
    rm -f "$temp_so"
    return 1
  fi

  unresolved_go_symbols="$(
    "$elf_reader" --dyn-syms --wide "$temp_so" 2>/dev/null |
      awk '$7 == "UND" && $8 ~ /\// { print $8 }'
  )"
  rm -f "$temp_so"
  if [[ -n "$unresolved_go_symbols" ]]; then
    echo "AWGBox libbox.so contains unresolved Go symbols:" >&2
    echo "$unresolved_go_symbols" >&2
    return 1
  fi
}

# Bind cached binaries to the exact source, toolchain and local Android patches.
# A structurally valid AAR from an older release must never skip an upgrade.
build_fingerprint() {
  {
    printf '%s\n' "$REPO" "$REF" "$EXPECTED_COMMIT" "$GO_TOOLCHAIN"
    sha256sum "$ROOT/scripts/build-awgbox-lib.sh" \
      "$ROOT/scripts/patches/awgbox-tor-android.patch" \
      "$ROOT/scripts/patches/awgbox-tor-log-noise.patch" | awk '{ print $1 }'
  } | sha256sum | awk '{ print $1 }'
}

artifact_is_valid() {
  output_is_valid && native_output_is_valid || return 1
  [[ -f "$OUTPUT.build-info" ]] || return 1
  local expected_info
  expected_info="$(build_fingerprint) $(sha256sum "$OUTPUT" | awk '{ print $1 }')"
  [[ "$(cat "$OUTPUT.build-info")" == "$expected_info" ]]
}

if [[ "$FORCE_REBUILD" != "1" ]] && artifact_is_valid; then
  echo "AWGBox AAR is already available and verified: $OUTPUT"
  exit 0
fi

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK containing an NDK}"
export ANDROID_HOME
command -v git >/dev/null || { echo "git is required to build AWGBox" >&2; exit 1; }
command -v go >/dev/null || { echo "Go is required to build AWGBox" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required to build AWGBox" >&2; exit 1; }

# AWGBox 1.15 is developed and tested with Go 1.25.5. Newer Go linkers can
# accept its private x/net/http2 linkname but leave that target undefined in the
# Android shared object, producing an UnsatisfiedLinkError during dlopen.
export GOTOOLCHAIN="$GO_TOOLCHAIN"
actual_go_toolchain="$(go env GOVERSION)"
if [[ "$actual_go_toolchain" != "$GO_TOOLCHAIN" ]]; then
  echo "AWGBox Go toolchain mismatch: expected $GO_TOOLCHAIN, got $actual_go_toolchain" >&2
  exit 1
fi

echo "Building AWGBox with $actual_go_toolchain"
mkdir -p "$ROOT/.artifacts" "$(dirname "$OUTPUT")"
LOCK_DIR="$ROOT/.artifacts/awgbox-build.lock"
while ! mkdir "$LOCK_DIR" 2>/dev/null; do
  if [[ "$FORCE_REBUILD" != "1" ]] && artifact_is_valid; then
    echo "AWGBox AAR was prepared by another Gradle process: $OUTPUT"
    exit 0
  fi
  if [[ ! -f "$LOCK_DIR/pid" ]] || ! kill -0 "$(cat "$LOCK_DIR/pid" 2>/dev/null)" 2>/dev/null; then
    rm -rf "$LOCK_DIR"
    continue
  fi
  sleep 1
done
echo "$$" > "$LOCK_DIR/pid"
trap 'rm -rf "$LOCK_DIR"' EXIT

# Another process may have completed while this process was waiting for the lock.
if [[ "$FORCE_REBUILD" != "1" ]] && artifact_is_valid; then
  echo "AWGBox AAR is already available and verified: $OUTPUT"
  exit 0
fi

if [[ -d "$WORK" ]]; then
  # Go's module cache is read-only by design; make it removable before
  # replacing a previous build workspace.
  chmod -R u+w "$WORK" 2>/dev/null || true
  rm -rf "$WORK"
fi
git clone --depth 1 --branch "$REF" "$REPO" "$WORK"
actual_commit="$(git -C "$WORK" rev-parse HEAD)"
if [[ "$actual_commit" != "$EXPECTED_COMMIT" ]]; then
  echo "AWGBox source revision mismatch" >&2
  echo "Expected: $EXPECTED_COMMIT" >&2
  echo "Actual:   $actual_commit" >&2
  exit 1
fi
git -C "$WORK" submodule update --init --depth 1

# Harden the Tor outbound for Android: wait for bootstrap and keep Tor's
# internal SOCKS listener on an app-private Unix socket instead of exposing an
# unauthenticated localhost TCP port. The current Tor executable is supplied by
# the Guardian Project Android package.
git -C "$WORK" apply "$ROOT/scripts/patches/awgbox-tor-android.patch"
git -C "$WORK" apply "$ROOT/scripts/patches/awgbox-tor-log-noise.patch"

GOBIN_DIR="$WORK/.bin"
mkdir -p "$GOBIN_DIR"
GOBIN="$GOBIN_DIR" go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12
GOBIN="$GOBIN_DIR" go install github.com/sagernet/gomobile/cmd/gobind@v0.1.12

# Keep the mobile core intentionally small. VLESS, VMess, SOCKS/HTTP, Tor and
# proxy chaining are part of the base build. AWG and uTLS are the only optional
# protocol features required by ProtonVPN-Next. Clash API is retained
# because libbox CommandServer uses its internal tracker even without an external
# controller. This excludes QUIC (Hysteria2/TUIC), gVisor, WireGuard, Tailscale,
# Naive, USB/IP, OpenVPN and OpenConnect.
python3 - "$WORK/cmd/internal/build_libbox/main.go" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
text = path.read_text()
text, count = re.subn(
    r'sharedTags = append\(sharedTags, "with_quic"[^\n]+',
    'sharedTags = append(sharedTags, "with_awg", "with_utls", "with_clash_api", "badlinkname", "tfogo_checklinkname0")',
    text,
    count=1,
)
if count != 1:
    raise SystemExit("Unable to locate the default libbox feature tags")
text = re.sub(
    r'\n\s*sharedTags = append\(sharedTags, "with_tailscale"[^\n]+',
    '',
    text,
    count=1,
)
# The project uses JDK 21; gomobile itself does not require the upstream helper's
# exact JDK 17 string check.
text = text.replace("\tcheckJavaVersion()", "\t// checkJavaVersion()")
path.write_text(text)
PY

# Upstream assumes tags start with "v", but amnezia-box release tags do not.
# Preserve the first digit in Libbox.version() and the embedded core version.
python3 - "$WORK/cmd/internal/build_shared/tag.go" <<'PY_TAG'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
if 'currentTag[1:]' not in text or 'currentTagRev[1:]' not in text:
    raise SystemExit("Unable to locate upstream version tag normalization")
text = text.replace('import (', 'import (\n\t"strings"', 1)
text = text.replace('currentTag[1:]', 'strings.TrimPrefix(currentTag, "v")')
text = text.replace('currentTagRev[1:]', 'strings.TrimPrefix(currentTagRev, "v")')
path.write_text(text)
PY_TAG

gofmt -w "$WORK/cmd/internal/build_libbox/main.go" "$WORK/cmd/internal/build_shared/tag.go"

# The generator locates gomobile through GOPATH/bin. Keep this cache outside
# the disposable source checkout so forced rebuilds do not redownload Go modules.
mkdir -p "$GOPATH_DIR/bin"
cp "$GOBIN_DIR/gomobile" "$GOPATH_DIR/bin/"
cp "$GOBIN_DIR/gobind" "$GOPATH_DIR/bin/"
(
  cd "$WORK"
  export GOPATH="$GOPATH_DIR"
  export PATH="$GOPATH_DIR/bin:$PATH"
  command -v gomobile >/dev/null || { echo "gomobile is missing from GOPATH/bin" >&2; exit 1; }
  command -v gobind >/dev/null || { echo "gobind is missing from GOPATH/bin" >&2; exit 1; }
  go run ./cmd/internal/build_libbox \
    -target android -platform android/arm64
)

TEMP_OUTPUT="$OUTPUT.tmp"
cp "$WORK/libbox.aar" "$TEMP_OUTPUT"
if ! OUTPUT="$TEMP_OUTPUT" output_is_valid || ! OUTPUT="$TEMP_OUTPUT" native_output_is_valid; then
  rm -f "$TEMP_OUTPUT"
  echo "Generated AWGBox AAR is corrupt or missing required arm64 contents" >&2
  exit 1
fi
actual_sha256="$(sha256sum "$TEMP_OUTPUT" | awk '{ print $1 }')"
printf '%s %s\n' "$(build_fingerprint)" "$actual_sha256" > "$TEMP_OUTPUT.build-info"
mv "$TEMP_OUTPUT" "$OUTPUT"
mv "$TEMP_OUTPUT.build-info" "$OUTPUT.build-info"
echo "AWGBox AAR built from pinned commit $EXPECTED_COMMIT"
echo "$actual_sha256  ${OUTPUT#$ROOT/}"
