#!/usr/bin/env bash
#
# verify-no-config-git.sh — fail if a `git` process runs while Gradle configures the app modules.
#
# Ticket 21 replaced three configuration-time `git rev-parse` calls with the `.git`-file reader in
# build-logic/git. That matters beyond tidiness: a process launched during configuration is re-run
# by every configure (and serializes parallel configuration), while the reader is a plain
# configuration-cache input. Nothing noticed a regression — a new eager
# `providers.exec { commandLine("git", …) }` would only show up as a slower configure.
#
# This gate puts a fake `git` first on PATH that records its argv and exits non-zero, then configures
# `:apps:android` and `:benchmark` twice against a throwaway project cache dir, so the pair is cold
# then reused and the developer's real configuration cache is untouched:
#
#   1. cold   — no configuration-cache entry, so every configuration-time reader really runs;
#   2. reused — the entry is restored, proving the no-git property survives a cache round-trip.
#
# Both runs must succeed and the recorded argv must stay empty (the argv is printed when it is not).
# The shim is used once by this script first: a shim that never records (e.g. PATH not reaching the
# build) would make the gate pass vacuously.
#
# Needs no Docker, device, browser or network of its own: both targets are `help` tasks, so Gradle
# stops after configuration, and the first run only resolves the plugin dependencies every other
# build already needs. `--no-daemon` is deliberate: a long-lived daemon keeps the environment it
# was started with, so the PATH shim would not reach `providers.exec` (a forked process) on a
# machine whose daemon is already warm. A single-use daemon inherits this shell's PATH.
# Budget: the cold run configures two modules (~10-25 s on a warm plugin/build cache); the reused
# run is a few seconds. Target well under a minute on a warm machine.
#
# Usage: scripts/ci/verify-no-config-git.sh
# Exit codes: 0 = pass, 1 = git ran at configuration time or a Gradle run failed, 2 = bad setup.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

command -v mktemp >/dev/null 2>&1 || { echo "ERROR: mktemp is required" >&2; exit 2; }

work="$(mktemp -d "${TMPDIR:-/tmp}/chipbox-no-config-git.XXXXXX")" || exit 2
trap 'rm -rf "$work"' EXIT

shim_dir="$work/bin"
cache_dir="$work/project-cache"
git_log="$work/git.log"
mkdir -p "$shim_dir" "$cache_dir"
: >"$git_log"

# The fake git: record the argv, then fail. Exiting non-zero also means a build that launches git
# and swallows the status can never pick up a real revision from it.
cat >"$shim_dir/git" <<SHIM
#!/usr/bin/env bash
# Fake git for scripts/ci/verify-no-config-git.sh: configuring must launch no git process, so record
# the invocation (printed on failure) and exit non-zero.
printf 'git %s\n' "\$*" >> $(printf '%q' "$git_log")
exit 1
SHIM
chmod +x "$shim_dir/git"

PATH="$shim_dir:$PATH"
export PATH
shim_path="$(command -v git)"
if [ "$shim_path" != "$shim_dir/git" ]; then
  echo "ERROR: the git shim is not first on PATH (git resolves to '$shim_path')" >&2
  exit 2
fi

# Positive control: prove the shim records before trusting an empty log below.
"$shim_dir/git" --version >/dev/null 2>&1
if [ ! -s "$git_log" ]; then
  echo "ERROR: the git shim recorded nothing — the gate would pass vacuously" >&2
  exit 2
fi
: >"$git_log"

# `--configuration-cache` is explicit even though gradle.properties enables it, so this check keeps
# testing the cache path if the default is ever flipped. --project-cache-dir is the throwaway dir:
# the configuration cache lives there, which is what makes run 1 cold and run 2 a reuse.
# -Pchipbox.skipNative matches the CI static-analysis context: the emulator `:native` modules then
# need no Android SDK/NDK/CMake, so this check runs on a box without them (as CI's lint job does).
gradle_help() { # <output-file>
  ./gradlew :apps:android:help :benchmark:help \
    --configuration-cache --console=plain --no-daemon -Pchipbox.skipNative \
    --project-cache-dir "$cache_dir" >"$1" 2>&1
}

failures=0
report_git() { # <label>
  echo "FAIL: $1 configuration launched $(wc -l <"$git_log" | tr -d ' ') git process(es):" >&2
  sed 's/^/    /' "$git_log" >&2
  failures=$((failures + 1))
}

run_one() { # <label> <output-file>; sets run_rc and run_elapsed
  local label="$1" out="$2" start
  start=$SECONDS
  gradle_help "$out"
  run_rc=$?
  run_elapsed=$((SECONDS - start))

  if [ "$run_rc" -ne 0 ]; then
    echo "FAIL: $label run failed (exit $run_rc); last 40 lines of $out:" >&2
    tail -n 40 "$out" | sed 's/^/    /' >&2
    failures=$((failures + 1))
  fi
  if [ -s "$git_log" ]; then report_git "$label"; : >"$git_log"; fi
}

cold_out="$work/cold.log"
run_one cold "$cold_out"
cold_seconds=$run_elapsed

reuse_out="$work/reuse.log"
run_one reused "$reuse_out"
reuse_seconds=$run_elapsed

# Run 2 must actually reuse the entry; a silently cold second run still proves no-git, but the
# "reused" half would stop testing what it claims (and the budget assumes the cache hit).
if [ "$failures" -eq 0 ] && ! grep -q "Reusing configuration cache" "$reuse_out"; then
  echo "FAIL: the second run did not report a configuration-cache reuse:" >&2
  cache_lines="$(grep -Ei "configuration cache" "$reuse_out" | tail -n 5)"
  if [ -n "$cache_lines" ]; then
    printf '%s\n' "$cache_lines" | sed 's/^/    /' >&2
  else
    echo "    (no 'configuration cache' line in $reuse_out)" >&2
  fi
  failures=$((failures + 1))
fi

if [ "$failures" -ne 0 ]; then
  echo "no-config-git invariant FAILED ($failures failure(s)); Gradle output kept in $work" >&2
  trap - EXIT # keep $work/ (cold.log, reuse.log) for the failure report
  exit 1
fi

echo "no-config-git invariant OK: 0 git processes while configuring :apps:android + :benchmark" \
  "(cold ${cold_seconds}s, reused ${reuse_seconds}s)"
