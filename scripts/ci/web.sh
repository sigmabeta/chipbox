#!/usr/bin/env bash
#
# web.sh — the local twin of CI's `web` job (and the opt-in Docker image smoke).
#
# Builds the WASM emulators + webpack bundle, asserts all nine loader/WASM pairs, runs the shared
# JS-side tests (jsNodeTest), and assembles the server distribution. In `image` mode it also builds
# the amd64 Docker image (or the host arch when buildx is unavailable) and smokes the container.
#
# Invoked by scripts/ci/verify.sh (`web`, opt-in `web-image`), or directly:
#   scripts/ci/web.sh          # bundle + bundle assertion + jsNodeTest + installDist
#   scripts/ci/web.sh image    # ... plus Docker image build + container smoke
#
# Exit codes: 0 = pass, 1 = failure, 3 = SKIP (no emsdk/yarn/node, or no Docker daemon/buildx for
# image mode). verify.sh maps 3 to SKIP, matching the device-axis convention.
#
# Environment:
#   EMSDK              emsdk checkout (default: $HOME/emsdk)
#   CHIPBOX_WEB_PORT   host port for the image smoke (default 18080)

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

MODE="${1:-web}"
EMSDK_DIR="${EMSDK:-$HOME/emsdk}"
PORT="${CHIPBOX_WEB_PORT:-18080}"

skip() {
  echo "SKIP: $*" >&2
  exit 3
}

# ---- toolchain gates -------------------------------------------------------
# Image mode is checked up front so a missing daemon is a clean SKIP before the expensive build.
if [ "$MODE" = "image" ] || [ "$MODE" = "web-image" ]; then
  command -v docker >/dev/null 2>&1 || skip "docker CLI is not on PATH"
  docker info >/dev/null 2>&1 || skip "Docker daemon is not running (image build needs it)"
fi

command -v node >/dev/null 2>&1 || skip "node is not on PATH (needed by the Kotlin/JS toolchain)"
command -v yarn >/dev/null 2>&1 || skip "yarn 1.x is not on PATH (auto-download is disabled)"
[ -f "$EMSDK_DIR/emsdk_env.sh" ] && [ -x "$EMSDK_DIR/upstream/emscripten/emcc" ] \
  || skip "emsdk not found at $EMSDK_DIR (set EMSDK or install emsdk: docker/emsdk-version pins the version)"
export EMSDK="$EMSDK_DIR"

GRADLE_FLAGS=(
  -Psage.js=true
  -Pchipbox.skipNative
  -Dorg.gradle.isolated-projects=false
  -Dorg.gradle.configureondemand=false
  --no-parallel
  --build-cache
  --console=plain
)

run_gradle() {
  echo "──▶ ./gradlew $*"
  ./gradlew "$@"
}

# ---- bundle + server -------------------------------------------------------
# `verifyWebBundle` depends on jsBrowserDistribution and asserts index.html, chipbox.js and every
# loader/WASM pair; installDist copies the same production bundle into the server distribution.
run_gradle :apps:js:verifyWebBundle :apps:server:installDist "${GRADLE_FLAGS[@]}" || exit 1
scripts/ci/verify-web-bundle.sh || exit 1

# Health/scan-state mapping + loopback-bind tests (plain JVM, no browser).
run_gradle :apps:server:test -Pchipbox.skipNative --build-cache --console=plain || exit 1

# The shared pure-logic suites (localStorage codecs, URL<->route mapping, model serialization).
# Test compilation is disabled for modules whose jsNodeTest is skipped (settings.gradle.kts), so
# this stays within the default Kotlin daemon instead of compiling unreachable Compose test code.
run_gradle jsNodeTest "${GRADLE_FLAGS[@]}" || exit 1

if [ "$MODE" != "image" ] && [ "$MODE" != "web-image" ]; then
  echo "web verification passed (bundle + WASM pairs + jsNodeTest + installDist)"
  exit 0
fi

# ---- Docker image + smoke (opt-in) ----------------------------------------
IMAGE="chipbox-server:verify"
PLATFORM=""
if docker buildx version >/dev/null 2>&1; then
  PLATFORM="linux/amd64"
  echo "──▶ docker buildx build --platform $PLATFORM --load -t $IMAGE ."
  docker buildx build --platform "$PLATFORM" --load -t "$IMAGE" . || exit 1
else
  echo "──▶ docker build -t $IMAGE . (buildx unavailable; host architecture)"
  docker build -t "$IMAGE" . || exit 1
fi

echo "──▶ scripts/ci/web-image-smoke.sh $IMAGE ${PLATFORM:+(platform $PLATFORM)}"
if [ -n "$PLATFORM" ]; then
  CHIPBOX_WEB_PORT="$PORT" scripts/ci/web-image-smoke.sh "$IMAGE" --platform "$PLATFORM" || exit 1
else
  CHIPBOX_WEB_PORT="$PORT" scripts/ci/web-image-smoke.sh "$IMAGE" || exit 1
fi

echo "web image verification passed ($IMAGE)"
