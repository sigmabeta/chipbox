#!/usr/bin/env bash
#
# doctor.sh — report which toolchains and verify axes this machine can run.
#
# Run this before starting work on a new machine. It is diagnostic, not a gate: it prints an
# `ok`/`warn`/`fail` line per dependency with the fix, then a matrix of the verify.sh axes.
# Exit code is 0 unless the repository or JDK is unusable.
#
# Usage: scripts/dev/doctor.sh

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

missing=0
warnings=0

ok()   { printf '  ok    %s\n' "$*"; }
warn() { printf '  warn  %s\n' "$*"; warnings=$((warnings + 1)); }
fail() { printf '  fail  %s\n' "$*"; missing=$((missing + 1)); }
head_() { printf '\n%s\n' "$*"; }

# ---- repository -------------------------------------------------------------
head_ "Repository"
if [ -d .git ]; then
  ok "git checkout: $ROOT"
else
  fail "not a git checkout: $ROOT"
fi

branch="$(git branch --show-current 2>/dev/null || true)"
[ -n "$branch" ] && ok "branch: $branch" || warn "detached HEAD (fine for a worktree, check before committing)"
if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
  warn "working tree is dirty (expected mid-task; verify.sh will warn too)"
else
  ok "working tree clean"
fi

if [ -f sage/README.md ]; then
  recorded="$(git ls-tree HEAD sage 2>/dev/null | awk '{print $3}')"
  checked="$(git -C sage rev-parse HEAD 2>/dev/null || true)"
  if [ -n "$recorded" ] && [ "$recorded" = "$checked" ]; then
    ok "sage submodule at the recorded pin: ${checked:0:8}"
  else
    warn "sage is at ${checked:0:8} but the superproject records ${recorded:0:8}"
    warn "pull latest per AGENTS.md; if the new tip breaks the build, flag it before other work"
  fi
  # `git submodule update --remote` follows the branch pinned in .gitmodules. Catch a checkout that
  # is behind that branch (e.g. an old `main` left over from before it was synced with `beta`).
  sage_branch="$(git config -f .gitmodules --get submodule.sage.branch 2>/dev/null || echo main)"
  sage_tip="$(git -C sage rev-parse --verify --quiet "origin/$sage_branch" 2>/dev/null || true)"
  if [ -n "$checked" ] && [ -n "$sage_tip" ] && [ "$checked" != "$sage_tip" ] \
      && git -C sage merge-base --is-ancestor "$checked" "$sage_tip" 2>/dev/null; then
    warn "sage is behind origin/$sage_branch (at ${checked:0:8}, $sage_branch ${sage_tip:0:8})"
    warn "run: git submodule update --remote sage"
  fi
  if [ -n "$(git -C sage status --porcelain 2>/dev/null)" ]; then
    warn "sage submodule has local changes"
  fi
else
  fail "sage/ submodule is not initialized (git submodule update --init sage)"
fi

# ---- core toolchain ---------------------------------------------------------
head_ "Core toolchain"
java_bin=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_bin="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  java_bin="$(command -v java)"
fi
if [ -n "$java_bin" ]; then
  jv="$("$java_bin" -version 2>&1 | head -1)"
  if printf '%s' "$jv" | grep -q 'version "'; then
    ok "java: $jv"
    case "$jv" in
      *\"21.*|*\"22.*|*\"23.*|*\"24.*|*\"25.*) : ;;
      *) warn "JDK 21+ expected by the Android/JVM builds (found: $jv)" ;;
    esac
  else
    fail "java at $java_bin is not usable ($jv); set JAVA_HOME to a JDK 21"
    if [ -d "$HOME/.gradle/jdks" ]; then
      hint="$(find "$HOME/.gradle/jdks" -maxdepth 5 -path '*/Contents/Home' -type d 2>/dev/null | head -1)"
      [ -n "$hint" ] && printf '        try: export JAVA_HOME="%s"\n' "$hint"
    fi
  fi
else
  fail "no java on PATH or JAVA_HOME (install JDK 21 and export JAVA_HOME)"
fi
gradle_version="$(./gradlew --version 2>/dev/null | awk '/^Gradle / {print $2; exit}')"
if [ -x ./gradlew ] && [ -n "$gradle_version" ]; then
  ok "Gradle wrapper: $gradle_version"
else
  fail "gradlew did not report a version (fix JAVA_HOME first)"
fi

# ---- native / performance toolchain -----------------------------------------
head_ "Native / performance toolchain"
if command -v cc >/dev/null 2>&1 || command -v gcc >/dev/null 2>&1 || command -v clang >/dev/null 2>&1; then
  ok "C compiler: $(command -v cc 2>/dev/null || command -v gcc 2>/dev/null || command -v clang)"
else
  fail "no C compiler (cc/gcc/clang) — native emulator builds unavailable"
fi
asan_tmp="$(mktemp -d 2>/dev/null || true)"
if [ -n "$asan_tmp" ]; then
  printf 'int main(void){return 0;}\n' > "$asan_tmp/t.c"
  cc_bin="${CC:-cc}"
  if command -v "$cc_bin" >/dev/null 2>&1 && "$cc_bin" -fsanitize=address "$asan_tmp/t.c" -o "$asan_tmp/a" >/dev/null 2>&1; then
    ok "ASan link: $cc_bin -fsanitize=address"
  else
    warn "ASan link fails; install libasan (or set CC) — ticket sanitizer gates will be BLOCKED"
  fi
  if command -v "$cc_bin" >/dev/null 2>&1 && "$cc_bin" -fsanitize=undefined "$asan_tmp/t.c" -o "$asan_tmp/u" >/dev/null 2>&1; then
    ok "UBSan link: $cc_bin -fsanitize=undefined"
  else
    warn "UBSan link fails; install libubsan — arithmetic-UB reports will be unavailable"
  fi
  rm -rf "$asan_tmp"
fi
if command -v perf >/dev/null 2>&1; then
  ok "perf: $(command -v perf)"
else
  warn "perf is not on PATH (use manual counters, or install linux-tools for this kernel)"
fi
if command -v valgrind >/dev/null 2>&1; then
  ok "valgrind: $(command -v valgrind)"
else
  warn "valgrind is not on PATH (use GCC ASan/UBSan for leak checks)"
fi
if command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1; then
  ok "MinGW-w64: $(command -v x86_64-w64-mingw32-gcc) (Windows JDK still required for the cross build)"
else
  warn "MinGW-w64 compiler is not on PATH (Windows x86_64 cross build unavailable)"
fi
browser=""
for c in chromium chromium-browser google-chrome google-chrome-stable; do
  if command -v "$c" >/dev/null 2>&1; then browser="$(command -v "$c")"; break; fi
done
if [ -n "$browser" ]; then
  ok "browser: $browser (headless WASM/audio gates)"
else
  warn "no Chromium/Chrome on PATH (browser audio/WASM gates unavailable)"
fi

# ---- web toolchain ----------------------------------------------------------
head_ "Web toolchain (-Psage.js=true)"
if command -v node >/dev/null 2>&1; then
  ok "node: $(node --version) (Docker/CI pin: $(cat docker/node-version 2>/dev/null || echo unknown))"
else
  fail "node is not on PATH (needed by Kotlin/JS; install Node $(cat docker/node-version 2>/dev/null || echo 22))"
fi
if command -v yarn >/dev/null 2>&1; then
  yv="$(yarn --version 2>/dev/null || true)"
  case "$yv" in
    1.*) ok "yarn: $yv" ;;
    *)   warn "yarn 1.x is required (found ${yv:-unknown}; the build disables auto-download)" ;;
  esac
else
  fail "yarn is not on PATH (install yarn@$(cat docker/yarn-version 2>/dev/null || echo 1.22.22))"
fi

emsdk="${EMSDK:-$HOME/emsdk}"
pin="$(cat docker/emsdk-version 2>/dev/null || echo unknown)"
if [ -f "$emsdk/emsdk_env.sh" ] && [ -x "$emsdk/upstream/emscripten/emcc" ]; then
  have="$(tr -d '"' < "$emsdk/upstream/emscripten/emscripten-version.txt" 2>/dev/null || echo unknown)"
  if [ "$have" = "$pin" ]; then
    ok "emsdk: $have (matches docker/emsdk-version)"
  else
    warn "emsdk: $have (docker/emsdk-version pins $pin)"
  fi
  ok "emcc: $emsdk/upstream/emscripten/emcc"
else
  fail "emsdk not found at $emsdk (set EMSDK or install emsdk $pin)"
fi

if command -v cmake >/dev/null 2>&1; then
  ok "cmake: $(cmake --version | head -1 | awk '{print $3}')"
else
  fail "cmake is not on PATH (needed by the WASM/native builds)"
fi

# ---- containers -------------------------------------------------------------
head_ "Containers"
if command -v docker >/dev/null 2>&1; then
  ok "docker CLI: $(docker --version 2>/dev/null | awk '{print $3}' | tr -d ',')"
  if docker info >/dev/null 2>&1; then
    ok "Docker daemon is reachable"
  else
    warn "Docker daemon is not running (image build/smoke will SKIP; start Docker/Colima)"
  fi
  if docker buildx version >/dev/null 2>&1; then
    ok "docker buildx available (multi-arch builds possible)"
  else
    warn "docker buildx is not installed (multi-arch release build unavailable)"
  fi
else
  warn "docker CLI is not on PATH (image axes will SKIP)"
fi

# ---- Android / device -------------------------------------------------------
head_ "Android"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] && [ -f local.properties ]; then
  sdk="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
if [ -n "$sdk" ] && [ -d "$sdk" ]; then
  ok "Android SDK: $sdk"
  if [ -d "$sdk/platforms" ] && [ -n "$(ls -A "$sdk/platforms" 2>/dev/null)" ]; then
    ok "installed platforms: $(ls "$sdk/platforms" | tr '\n' ' ')"
  else
    warn "SDK has no installed platforms (Android axes need platform/build-tools + licenses)"
  fi
  if [ -d "$sdk/licenses" ] && [ -n "$(ls -A "$sdk/licenses" 2>/dev/null)" ]; then
    ok "SDK licenses accepted"
  else
    warn "SDK licenses are not accepted (run sdkmanager --licenses)"
  fi
else
  warn "Android SDK not found (ANDROID_HOME / ANDROID_SDK_ROOT / sdk.dir); Android axes unavailable"
fi
# settings.gradle.kts copies the root local.properties into sage/ when it is missing, but a stale
# sage/local.properties is easy to leave behind and the composite build honours it.
if [ -f sage/local.properties ]; then
  sage_sdk="$(sed -n 's/^sdk\.dir=//p' sage/local.properties | head -1)"
  if [ -n "$sage_sdk" ] && [ ! -d "$sage_sdk" ]; then
    warn "sage/local.properties sdk.dir points at a missing SDK: $sage_sdk"
    warn "remove sage/local.properties (settings.gradle.kts will re-sync it) or fix sdk.dir"
  fi
fi
if command -v adb >/dev/null 2>&1; then
  devices="$(adb devices 2>/dev/null | awk 'NR > 1 && $2 == "device" {print $1}' | tr '\n' ' ')"
  [ -n "$devices" ] && ok "attached devices: $devices" || warn "no attached device (device axes will SKIP)"
else
  warn "adb is not on PATH (device axes unavailable)"
fi

# ---- dev scripts ------------------------------------------------------------
# rg/fd serve .scratch navigation (docs/agents/issue-tracker.md); ImageMagick is
# required by scripts/dev/paparazzi-diff.sh (magick/compare/montage).
head_ "Dev scripts"
dev_scripts_ok=yes
if command -v rg >/dev/null 2>&1; then
  ok "rg: $(rg --version 2>/dev/null | awk 'NR == 1 { print $2 }') (.scratch navigation)"
else
  warn "rg is not on PATH (.scratch navigation falls back to grep/find; brew install ripgrep)"
  dev_scripts_ok=no
fi
if command -v fd >/dev/null 2>&1; then
  ok "fd: $(fd --version 2>/dev/null | awk '{ print $2 }') (.scratch navigation)"
else
  warn "fd is not on PATH (.scratch navigation falls back to find; brew install fd)"
  dev_scripts_ok=no
fi
for c in magick compare montage; do
  if command -v "$c" >/dev/null 2>&1; then
    ok "$c: $(command -v "$c")"
  else
    fail "$c is not on PATH (paparazzi-diff.sh needs ImageMagick; brew install imagemagick)"
    dev_scripts_ok=no
  fi
done
if [ "$dev_scripts_ok" = no ]; then
  printf '        install all: brew install ripgrep fd imagemagick\n'
fi

# ---- verify matrix ----------------------------------------------------------
head_ "Verify axes"
web_ready=yes
for c in node yarn cmake; do command -v "$c" >/dev/null 2>&1 || web_ready=no; done
[ -x "$emsdk/upstream/emscripten/emcc" ] || web_ready=no
printf '  %-28s %s\n' "verify.sh web" "$web_ready"
if docker info >/dev/null 2>&1 && docker buildx version >/dev/null 2>&1; then
  printf '  %-28s %s\n' "verify.sh web-image" "yes"
else
  printf '  %-28s %s\n' "verify.sh web-image" "no (needs daemon + buildx)"
fi
printf '  %-28s %s\n' "verify.sh docker-release" "yes (static)"
if [ -n "$sdk" ] && [ -d "$sdk/platforms" ] && [ -n "$(ls -A "$sdk/platforms" 2>/dev/null)" ]; then
  printf '  %-28s %s\n' "verify.sh (JVM/Android)" "yes"
else
  printf '  %-28s %s\n' "verify.sh (JVM/Android)" "no (needs Android SDK platforms)"
fi
if command -v adb >/dev/null 2>&1; then
  printf '  %-28s %s\n' "verify.sh --device-*" "yes if a device is attached"
else
  printf '  %-28s %s\n' "verify.sh --device-*" "no (needs adb/device)"
fi

head_ "Summary"
printf '  %d missing, %d warning(s)\n' "$missing" "$warnings"
if [ "$missing" -gt 0 ]; then
  printf '  Fix the failed lines above before running the matching verify axes or dev scripts.\n'
fi
exit 0
