#!/usr/bin/env bash
#
# verify-docker-release.sh — static gate for the multi-arch image + release wiring.
#
# The Docker image cannot be built on every dev box (no daemon/cmake/emsdk) and the two-architecture
# build needs QEMU, so this cheap check pins the invariants that are easy to regress:
#
#   Dockerfile
#     - web/WASM stage on $BUILDPLATFORM (built once, no QEMU for Gradle/emsdk)
#     - native vgmstream stage on $TARGETPLATFORM (per-arch compiler) and an ELF e_machine assert
#       that fails the build when the library does not match $TARGETARCH
#     - runtime stage on $TARGETPLATFORM, glibc (no alpine), non-root, /data volume, /music
#       convention, curl healthcheck, no-auth labels, and a build-time check that the server jar
#       carries static/index.html + all nine WASM pairs
#     - Dockerfile ARG defaults match docker/{node,yarn,emsdk}-version (the CI pins)
#   .github/workflows/release.yml
#     - a web-image job: both linux platforms, push:false, no login/push/credentials, OCI tar
#       output, gha layer cache, QEMU for the arm64 native stage, and the amd64/arm64 checks
#   .github/workflows/ci.yml
#     - the PR web job still builds amd64 and smokes it through the shared script
#
# Usage: scripts/ci/verify-docker-release.sh
# Exit codes: 0 = pass, 1 = invariant broken, 2 = missing tool.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKERFILE="$ROOT/Dockerfile"
CI="$ROOT/.github/workflows/ci.yml"
RELEASE="$ROOT/.github/workflows/release.yml"
MANIFEST="$ROOT/apps/js/wasm-emulators.txt"

failures=0
fail() {
  echo "FAIL: $*" >&2
  failures=$((failures + 1))
}

command -v ruby >/dev/null 2>&1 || {
  echo "ERROR: ruby is required to parse the workflow YAML" >&2
  exit 2
}
command -v python3 >/dev/null 2>&1 || {
  echo "ERROR: python3 is required for the OCI index check" >&2
  exit 2
}

for f in "$DOCKERFILE" "$CI" "$RELEASE" "$MANIFEST"; do
  [ -f "$f" ] || fail "missing $f"
done
[ "$failures" -eq 0 ] || exit 1

# ---- Dockerfile ------------------------------------------------------------
stage() {
  grep -Eq "^FROM --platform=\\\$$1 .* AS $2[[:space:]]*$" "$DOCKERFILE"
}

stage BUILDPLATFORM web || fail "web stage must be FROM --platform=\$BUILDPLATFORM"
stage TARGETPLATFORM native || fail "native stage must be FROM --platform=\$TARGETPLATFORM"
stage TARGETPLATFORM runtime || fail "runtime stage must be FROM --platform=\$TARGETPLATFORM"

grep -q '^ARG TARGETARCH' "$DOCKERFILE" \
  || fail "Dockerfile must declare ARG TARGETARCH to assert the native lib architecture"
grep -q 'jvm-native/out/linux/vgmstream/host' "$DOCKERFILE" \
  || fail "Dockerfile must read the host vgmstream output path (out/<target>/vgmstream/host)"
grep -q ':apps:jvm:buildHostNativeVgmstream' "$DOCKERFILE" \
  || fail "native stage must build :apps:jvm:buildHostNativeVgmstream"
grep -q -- '-Pchipbox.cmake=' "$DOCKERFILE" \
  || fail "native stage must pass -Pchipbox.cmake (the stage's per-arch CMake)"
grep -q '3e 00' "$DOCKERFILE" && grep -q 'b7 00' "$DOCKERFILE" \
  || fail "Dockerfile must assert ELF e_machine for both amd64 (3e 00) and arm64 (b7 00)"
grep -q 'static/wasm' "$DOCKERFILE" \
  || fail "runtime stage must assert the server jar carries the WASM assets"
grep -q 'wasm-emulators.txt' "$DOCKERFILE" \
  || fail "runtime stage must use apps/js/wasm-emulators.txt (single source of the nine pairs)"
grep -q 'verify-web-bundle.sh' "$DOCKERFILE" \
  || fail "web stage must assert the built bundle (scripts/ci/verify-web-bundle.sh)"

grep -Eqi '^FROM .*alpine' "$DOCKERFILE" && fail "runtime must be glibc (found an alpine base image)"
grep -q '^USER 10001:10001' "$DOCKERFILE" || fail "runtime must run non-root (USER 10001:10001)"
grep -Eq '^VOLUME \["/data"\]' "$DOCKERFILE" || fail "runtime must declare /data as a volume"
grep -q '^HEALTHCHECK' "$DOCKERFILE" || fail "runtime must define a HEALTHCHECK"
grep -q 'NO AUTHENTICATION' "$DOCKERFILE" || fail "image labels must carry the no-auth warning"

# The Java library path wiring + the /music convention.
grep -q 'CHIPBOX_LIBRARY_DIR=/music' "$DOCKERFILE" || fail "runtime must default the library to /music"
grep -q 'CHIPBOX_WORK_DIR=/data' "$DOCKERFILE" || fail "runtime must default the work dir to /data"

# Pins: one source shared by the image and the workflows.
for name in node yarn emsdk; do
  pin="$(cat "$ROOT/docker/$name-version" 2>/dev/null)" || fail "missing docker/$name-version"
  upper="$(echo "$name" | tr '[:lower:]' '[:upper:]')"
  grep -q "ARG ${upper}_VERSION=$pin" "$DOCKERFILE" \
    || fail "Dockerfile ARG ${upper}_VERSION must match docker/$name-version ($pin)"
done

# Nine emulators, stated once.
emulators="$(awk 'NF && $0 !~ /^#/ { n++ } END { print n + 0 }' "$MANIFEST")"
[ "$emulators" -eq 9 ] || fail "apps/js/wasm-emulators.txt must list 9 emulators (found $emulators)"

# Shell/Python syntax for the helpers wired into CI.
bash -n "$ROOT/scripts/ci/web-image-smoke.sh" || fail "scripts/ci/web-image-smoke.sh has a syntax error"
bash -n "$ROOT/scripts/ci/web.sh" || fail "scripts/ci/web.sh has a syntax error"
bash -n "$ROOT/scripts/ci/verify.sh" || fail "scripts/ci/verify.sh has a syntax error"
bash -n "$ROOT/scripts/ci/verify-docker-release.sh" || fail "this script has a syntax error"
python3 -c 'import sys; compile(open(sys.argv[1]).read(), sys.argv[1], "exec")' \
  "$ROOT/scripts/ci/verify-oci-index.py" \
  || fail "scripts/ci/verify-oci-index.py does not compile"
python3 "$ROOT/scripts/ci/verify-oci-index.py" --self-test \
  || fail "scripts/ci/verify-oci-index.py self-test failed"

# Dockerfile RUN instructions are shell scripts. There is no Docker daemon on most dev boxes (and
# no buildx here), so syntax-check them instead of trusting a visual read. The image's stages use
# either /bin/sh or bash, and every command below is POSIX, so `sh -n` covers both.
python3 - "$DOCKERFILE" <<'PY'
import re
import subprocess
import sys

lines = open(sys.argv[1]).read().splitlines()
commands = []
i = 0
while i < len(lines):
    line = lines[i]
    if line.startswith("RUN "):
        cmd = line[4:]
        while cmd.rstrip().endswith("\\") and i + 1 < len(lines):
            i += 1
            cmd += "\n" + lines[i]
        commands.append(cmd)
    i += 1

bad = 0
for number, cmd in enumerate(commands, 1):
    result = subprocess.run(["sh", "-n"], input=cmd, text=True, capture_output=True)
    if result.returncode != 0:
        sys.stderr.write("RUN #%d has a shell syntax error:\n%s\n" % (number, result.stderr))
        bad = 1

for line_index, line in enumerate(lines):
    if not line.startswith("HEALTHCHECK "):
        continue
    # HEALTHCHECK can span a continuation line ending in `CMD ...`.
    command = line
    index = line_index
    while command.rstrip().endswith("\\") and index + 1 < len(lines):
        index += 1
        command += "\n" + lines[index]
    match = re.search(r"\bCMD\s+(.*)", command, re.S)
    if not match:
        sys.stderr.write("HEALTHCHECK has no CMD\n")
        bad = 1
        continue
    result = subprocess.run(
        ["sh", "-n"], input=match.group(1), text=True, capture_output=True
    )
    if result.returncode != 0:
        sys.stderr.write("HEALTHCHECK CMD has a shell syntax error:\n%s\n" % result.stderr)
        bad = 1

sys.exit(bad)
PY
[ $? -eq 0 ] || fail "Dockerfile RUN/HEALTHCHECK shell syntax is invalid"

# ---- workflows ------------------------------------------------------------
ruby - "$CI" "$RELEASE" <<'RUBY'
require 'yaml'

ci_path, release_path = ARGV
ci = YAML.load_file(ci_path)
release = YAML.load_file(release_path)

def job!(doc, name)
  jobs = doc['jobs'] || {}
  jobs[name] || raise("missing job: #{name}")
end

def steps(job)
  job['steps'] || []
end

def build_step(steps)
  steps.find { |s| s['uses'].to_s.start_with?('docker/build-push-action@') }
end

def check(cond, message)
  raise(message) unless cond
end

# --- PR web job: keep the amd64 build + smoke from ticket 01 intact. ---
web = job!(ci, 'web')
check(web['needs'] == 'static-analysis', 'CI web job must stay behind static-analysis')
check(steps(web).any? { |s| s.dig('run').to_s.include?('scripts/ci/verify-web-bundle.sh') },
      'CI web job must still assert the bundle')
pr_build = build_step(steps(web))
check(!pr_build.nil?, 'CI web job must build the image')
pr_with = pr_build['with'] || {}
check(pr_with['platforms'].to_s.delete(' ') == 'linux/amd64',
      'CI web job must build linux/amd64')
check(pr_with['push'] == false, 'CI web job must not push')
check(pr_with['load'] == true, 'CI web job must load the amd64 image for the smoke')
check(pr_with['build-args'].to_s.include?('EMSDK_VERSION='),
      'CI web image build must pass the pinned EMSDK_VERSION')
check(steps(web).any? { |s| s.dig('run').to_s.include?('scripts/ci/web-image-smoke.sh') },
      'CI web job must smoke through scripts/ci/web-image-smoke.sh')
check(!steps(web).any? { |s| s['uses'].to_s.include?('docker/login-action') },
      'CI web job must not log in to a registry')

# --- release web-image job: both arches, OCI tar, no registry. ---
img = job!(release, 'web-image')
check((img['permissions'] || {})['contents'] == 'read',
      'release web-image job must declare permissions: contents: read')
check(!img.to_s.include?('secrets.'),
      'release web-image job must not consume credentials/secrets')
check(!steps(img).any? { |s| s['uses'].to_s.include?('docker/login-action') },
      'release web-image job must not log in to a registry')

img_steps = steps(img)
check(img_steps.any? { |s| s['uses'].to_s.start_with?('docker/setup-qemu-action@') },
      'release web-image job must register QEMU for the arm64 native stage (setup-qemu-action)')
check(img_steps.any? { |s| s['uses'].to_s.start_with?('docker/setup-buildx-action@') },
      'release web-image job must set up buildx')

multi = img_steps.find do |s|
  s['uses'].to_s.start_with?('docker/build-push-action@') &&
    s.dig('with', 'platforms').to_s.include?(',')
end
check(!multi.nil?, 'release web-image job must build more than one platform in one build')
multi_with = multi['with'] || {}
platforms = multi_with['platforms'].to_s.delete(' ').split(',').sort
check(platforms == %w[linux/amd64 linux/arm64],
      "release build must target linux/amd64 + linux/arm64 (got #{platforms.join(',')})")
check(multi_with['push'] == false, 'release build must not push')
check(multi_with['outputs'].to_s.include?('type=oci'),
      'multi-arch release output must be an OCI archive (type=oci)')
check(multi_with['load'] != true, 'a multi-platform build cannot be --loaded')
check(multi_with['cache-from'].to_s.include?('type=gha'),
      'release build must import a gha layer cache')
check(multi_with['cache-to'].to_s.include?('type=gha'),
      'release build must export a gha layer cache')
check(multi_with['build-args'].to_s.include?('EMSDK_VERSION='),
      'release build must pass the pinned EMSDK_VERSION')
check(img_steps.any? { |s| s.dig('run').to_s.include?('mkdir -p build') },
      'release job must create the OCI output directory before the build')

check(img_steps.any? { |s| s.dig('run').to_s.include?('verify-oci-index.py') },
      'release job must verify the OCI archive contains both platforms')
check(img_steps.any? do |s|
  run = s.dig('run').to_s
  run.include?('web-image-smoke.sh') && run.include?('--contents-only') && run.include?('arm64')
end, 'release job must verify the arm64 image contents')
check(img_steps.any? do |s|
  run = s.dig('run').to_s
  run.include?('web-image-smoke.sh') && run.include?('amd64') && !run.include?('--contents-only')
end, 'release job must run the amd64 image smoke')

# No push anywhere in the release workflow's container job.
check(!img.to_s.include?('docker push') && !img.to_s.include?('--push'),
      'release image job must not push')

puts 'Workflow wiring OK: PR amd64 smoke + release amd64/arm64 OCI build, no registry'
RUBY
[ $? -eq 0 ] || failures=$((failures + 1))

if [ "$failures" -ne 0 ]; then
  echo "Docker/release invariants FAILED ($failures check(s))" >&2
  exit 1
fi

echo "Docker/release invariants OK: platform stages, both arches, no push, native lib arch, pins"
