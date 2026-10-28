#!/usr/bin/env bash
#
# web-image-smoke.sh — verify a built chipbox-server image without a registry.
#
# Two modes:
#   full      start the container, wait for /api/health, fetch the SPA + every WASM loader/WASM
#             pair, and inspect the server jar.
#   contents  do not run the container (no QEMU needed): `docker create` + `docker cp` the server
#             distribution out of the image and check that the server jar carries all nine emulator
#             pairs. This is how the release job proves the arm64 image's contents on an amd64 runner.
#
# The manifest (apps/js/wasm-emulators.txt) is the single source of truth for the nine pairs, so a
# new emulator cannot be added to the bundle without this check following it.
#
# Usage:
#   scripts/ci/web-image-smoke.sh <image> [--platform linux/amd64|linux/arm64] [--contents-only]
#
# Environment:
#   CHIPBOX_WEB_PORT   host port for the full-mode smoke (default 18080)
#
# Exit codes: 0 = pass, 1 = failure, 2 = usage.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST="$ROOT/apps/js/wasm-emulators.txt"
PORT="${CHIPBOX_WEB_PORT:-18080}"

usage() {
  echo "usage: $0 <image> [--platform <platform>] [--contents-only]" >&2
  exit 2
}

[ $# -ge 1 ] || usage
IMAGE="$1"
shift
PLATFORM=""
MODE="full"
while [ $# -gt 0 ]; do
  case "$1" in
    --platform)
      [ $# -ge 2 ] || usage
      PLATFORM="$2"
      shift
      ;;
    --contents-only) MODE="contents" ;;
    -h|--help) usage ;;
    *) echo "unknown argument: $1" >&2; usage ;;
  esac
  shift
done

fail() {
  echo "FAILED: $*" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || fail "docker CLI is not on PATH"
docker info >/dev/null 2>&1 || fail "Docker daemon is not running"
command -v unzip >/dev/null 2>&1 || fail "unzip is required to inspect the server jar"
[ -f "$MANIFEST" ] || fail "missing manifest: $MANIFEST"

platform_args=()
if [ -n "$PLATFORM" ]; then
  platform_args=(--platform "$PLATFORM")
fi

# Image architecture as recorded by the daemon (works for a cross-loaded image too).
image_arch="$(docker image inspect --format '{{.Architecture}}' "$IMAGE" 2>/dev/null)" \
  || fail "image not found: $IMAGE"

TMP="$(mktemp -d)"
SMOKE_OK=0
cleanup() {
  if [ "${SMOKE_OK:-0}" != 1 ] && [ -n "${RUN_NAME:-}" ]; then
    docker logs "$RUN_NAME" 2>/dev/null || true
  fi
  if [ -n "${CID:-}" ]; then docker rm -f "$CID" >/dev/null 2>&1 || true; fi
  if [ -n "${RUN_NAME:-}" ]; then
    docker rm -f "$RUN_NAME" >/dev/null 2>&1 || true
  fi
  rm -rf "$TMP"
}
trap cleanup EXIT

# ---- contents (no container execution) ------------------------------------
CID="$(docker create ${platform_args[@]+"${platform_args[@]}"} "$IMAGE")" \
  || fail "docker create failed for $IMAGE"
docker cp "$CID:/opt/chipbox" "$TMP/chipbox" >/dev/null \
  || fail "image has no /opt/chipbox distribution"
docker rm "$CID" >/dev/null 2>&1 || true
CID=""

jar=""
jar_listing="$TMP/jar-contents.txt"
for candidate in "$TMP"/chipbox/lib/*.jar; do
  [ -f "$candidate" ] || continue
  # Capture the listing instead of `unzip | grep -q`: grep -q exits on the first match and the
  # resulting SIGPIPE on unzip would trip `set -o pipefail` even though the entry was found.
  if unzip -l "$candidate" >"$jar_listing" 2>/dev/null \
      && grep -q 'static/index.html' "$jar_listing"; then
    jar="$candidate"
    break
  fi
done
[ -n "$jar" ] || fail "no server jar in /opt/chipbox/lib contains static/index.html"

unzip -l "$jar" >"$jar_listing" || fail "cannot list $jar"
grep -q 'static/index.html' "$jar_listing" || fail "server jar is missing static/index.html"
grep -q 'static/chipbox.js' "$jar_listing" || fail "server jar is missing static/chipbox.js"

emulator_count=0
while IFS='|' read -r dir output display; do
  dir="$(echo "$dir" | xargs)"
  output="$(echo "$output" | xargs)"
  [ -z "$dir" ] && continue
  case "$dir" in \#*) continue ;; esac
  [ -n "$output" ] || fail "bad manifest line in $MANIFEST"
  emulator_count=$((emulator_count + 1))
  grep -q "static/wasm/$output.js" "$jar_listing" \
    || fail "server jar is missing static/wasm/$output.js"
  grep -q "static/wasm/$output.wasm" "$jar_listing" \
    || fail "server jar is missing static/wasm/$output.wasm"
done <"$MANIFEST"
[ "$emulator_count" -eq 9 ] || fail "expected 9 emulators in $MANIFEST, found $emulator_count"

echo "Image contents OK: $IMAGE ($image_arch, 9 WASM loader/WASM pairs)"
if [ "$MODE" = "contents" ]; then
  SMOKE_OK=1
  exit 0
fi

# ---- full runtime smoke ----------------------------------------------------
MUSIC="$TMP/music"
DATA="$TMP/data"
mkdir -p "$MUSIC" "$DATA"
chmod 777 "$DATA" # the container runs as uid 10001

RUN_NAME="chipbox-web-smoke-$$"
docker run -d --name "$RUN_NAME" \
  ${platform_args[@]+"${platform_args[@]}"} \
  -p "127.0.0.1:$PORT:8080" \
  -v "$MUSIC:/music:ro" \
  -v "$DATA:/data" \
  "$IMAGE" >/dev/null || fail "docker run failed"

health=""
for _ in $(seq 1 120); do
  if curl -fsS -o "$TMP/health.json" "http://127.0.0.1:$PORT/api/health" 2>/dev/null; then
    health="$(cat "$TMP/health.json")"
    break
  fi
  sleep 1
done
[ -n "$health" ] || fail "/api/health never became ready"
echo "$health"
case "$health" in
  *'"ok":true'*) ;;
  *) fail "/api/health did not report ok:true" ;;
esac
case "$health" in
  *libraryDir*) fail "/api/health leaks libraryDir" ;;
esac

curl -fsS -o /dev/null "http://127.0.0.1:$PORT/" || fail "/ did not serve index.html"
curl -fsS -o /dev/null "http://127.0.0.1:$PORT/chipbox.js" || fail "/chipbox.js missing"

while IFS='|' read -r dir output display; do
  dir="$(echo "$dir" | xargs)"
  output="$(echo "$output" | xargs)"
  [ -z "$dir" ] && continue
  case "$dir" in \#*) continue ;; esac
  curl -fsS -o /dev/null "http://127.0.0.1:$PORT/wasm/$output.js" \
    || fail "/wasm/$output.js missing"
  curl -fsS -o /dev/null "http://127.0.0.1:$PORT/wasm/$output.wasm" \
    || fail "/wasm/$output.wasm missing"
done <"$MANIFEST"

echo "Web image smoke passed: $IMAGE ($image_arch, port $PORT)"
SMOKE_OK=1
