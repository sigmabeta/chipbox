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
run_gradle jsNodeTest "${GRADLE_FLAGS[@]}" || exit 1

if [ "$MODE" != "image" ] && [ "$MODE" != "web-image" ]; then
  echo "web verification passed (bundle + WASM pairs + jsNodeTest + installDist)"
  exit 0
fi

# ---- Docker image + smoke (opt-in) ----------------------------------------
IMAGE="chipbox-server:verify"
if docker buildx version >/dev/null 2>&1; then
  echo "──▶ docker buildx build --platform linux/amd64 --load -t $IMAGE ."
  docker buildx build --platform linux/amd64 --load -t "$IMAGE" . || exit 1
else
  echo "──▶ docker build -t $IMAGE . (buildx unavailable; host architecture)"
  docker build -t "$IMAGE" . || exit 1
fi

TMP="$(mktemp -d)"
MUSIC="$TMP/music"
DATA="$TMP/data"
mkdir -p "$MUSIC" "$DATA"
chmod 777 "$DATA" # container runs as uid 10001

cleanup() {
  docker logs chipbox-web-verify 2>/dev/null || true
  docker rm -f chipbox-web-verify >/dev/null 2>&1 || true
  rm -rf "$TMP"
}
trap cleanup EXIT

echo "──▶ docker run $IMAGE (port $PORT, /music ro, /data persistent)"
docker run -d --name chipbox-web-verify \
  -p "127.0.0.1:$PORT:8080" \
  -v "$MUSIC:/music:ro" \
  -v "$DATA:/data" \
  "$IMAGE" || exit 1

health=""
for _ in $(seq 1 60); do
  if health="$(curl -fsS "http://127.0.0.1:$PORT/api/health" 2>/dev/null)"; then break; fi
  health=""
  sleep 1
done
if [ -z "$health" ]; then
  echo "FAILED: /api/health never became ready" >&2
  exit 1
fi
echo "$health"
case "$health" in
  *'"ok":true'*) ;;
  *) echo "FAILED: /api/health did not report ok:true" >&2; exit 1 ;;
esac
case "$health" in
  *libraryDir*) echo "FAILED: /api/health leaks libraryDir" >&2; exit 1 ;;
esac

curl -fsS "http://127.0.0.1:$PORT/" | grep -qi '<html' \
  || { echo "FAILED: / did not serve index.html" >&2; exit 1; }
curl -fsS "http://127.0.0.1:$PORT/chipbox.js" | head -c 32 >/dev/null \
  || { echo "FAILED: /chipbox.js missing" >&2; exit 1; }
curl -fsS "http://127.0.0.1:$PORT/wasm/chipbox_gme.js" | head -c 32 >/dev/null \
  || { echo "FAILED: /wasm/chipbox_gme.js missing" >&2; exit 1; }
curl -fsS "http://127.0.0.1:$PORT/wasm/chipbox_gme.wasm" | head -c 32 >/dev/null \
  || { echo "FAILED: /wasm/chipbox_gme.wasm missing" >&2; exit 1; }

docker exec chipbox-web-verify test -s /opt/chipbox/lib/native/libvgmstream.so \
  || { echo "FAILED: image has no native vgmstream library" >&2; exit 1; }
docker exec chipbox-web-verify sh -c '! ldd /opt/chipbox/lib/native/libvgmstream.so | grep -q "not found"' \
  || { echo "FAILED: vgmstream library has unresolved links" >&2; exit 1; }

echo "web image verification passed ($IMAGE)"
