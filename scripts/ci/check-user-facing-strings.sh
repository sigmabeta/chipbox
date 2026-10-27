#!/usr/bin/env bash
#
# check-user-facing-strings.sh — fail on hardcoded English in a user-facing snackbar.
#
# CODING_STANDARDS.md says every user-facing string goes through `ChipboxStringId` +
# `strings-*.xml` (resolved via `StringProvider`). The judgement part — what counts as
# user-facing — stays human. This is the mechanical part: the first argument to
# `ChipboxEvent.ShowSnackbar(...)` must not be a string literal (including an interpolated
# template and a multiline call whose literal starts on the next line). It must be an expression
# such as `stringProvider.getString(...)` / `stringProvider.getStringOneArg(...)`.
#
# Scope (deliberately narrow, so the result is deterministic and reviewable):
#   SCANNED  Kotlin files in a main source set:
#              src/commonMain, src/main (jvmSharedMain), src/androidMain, src/jvmMain, src/jsMain
#            Tracked files plus untracked-not-ignored files, so a new file is caught before commit.
#   EXCLUDED tests (src/commonTest, src/test, src/*Test — already outside the include list)
#   EXCLUDED screenshot/Paparazzi code and previews: files in a `screenshot/` module directory,
#            paths under a `preview/` directory, and `*Preview*.kt` files
#   EXCLUDED diagnostic logs: only `ChipboxEvent.ShowSnackbar` is inspected, so `hatchet.*`/
#            logger calls (exempt per CODING_STANDARDS.md) can never match
#   NOT YET checked: other user-facing literals (`Notif(description = ...)`, error strings, empty
#            states). The snackbar is the observed defect; widen this check in a follow-up.
#
# Matching is textual and conservative: a commented-out `// ChipboxEvent.ShowSnackbar("...")` is
# skipped (full-line comments only), and anything it cannot parse as a literal passes.
#
# Output: one `file:line: message` per violation, sorted by path then line. Exit 0 = clean,
# 1 = violation(s) found.
#
# Usage: scripts/ci/check-user-facing-strings.sh
#
# bash 3.2 compatible (macOS ships 3.2): no associative arrays, mapfile, or ${var,,}.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 2

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "check-user-facing-strings: not inside a git work tree" >&2
  exit 2
fi

is_scanned_source_set() {
  case "$1" in
    */src/commonMain/*|*/src/main/*|*/src/androidMain/*|*/src/jvmMain/*|*/src/jsMain/*)
      return 0 ;;
    *)
      return 1 ;;
  esac
}

is_excluded() {
  case "$1" in
    */screenshot/*|*/preview/*|*Preview*.kt|*ChipboxPreviewStrings.kt)
      return 0 ;;
    *)
      return 1 ;;
  esac
}

# One stateless pass per file: find each `ChipboxEvent.ShowSnackbar(` occurrence, then look at the
# first non-blank character of its argument list (crossing line breaks). `"` (or `"""`) means a
# literal; `$file:$line` reports the line of the call, which is what a reviewer opens.
CHECK_AWK='
{
  lines[++n] = $0
}
END {
  for (i = 1; i <= n; i++) {
    s = lines[i]
    trimmed = s
    sub(/^[ \t]+/, "", trimmed)
    if (trimmed ~ /^\/\//) continue

    pos = index(s, "ChipboxEvent.ShowSnackbar")
    if (pos == 0) continue

    rest = substr(s, pos + length("ChipboxEvent.ShowSnackbar"))
    if (rest !~ /^[ \t]*\(/) continue
    sub(/^[ \t]*\(/, "", rest)

    # The argument may start on a following line (`ShowSnackbar(\n    \"...\"\n)`).
    j = i
    while (rest ~ /^[ \t]*$/ && j < n) {
      j++
      rest = lines[j]
    }
    sub(/^[ \t]+/, "", rest)

    if (rest ~ /^"/) {
      printf "%s:%d: ShowSnackbar message is a hardcoded literal; add a ChipboxStringId + strings-*.xml value and resolve it through StringProvider\n", FILENAME, i
    }
  }
}
'

violations=0
files="$(git ls-files --cached --others --exclude-standard | LC_ALL=C sort -u)"
# Process substitution keeps the loop in this shell, so `violations` survives.
while IFS= read -r file; do
  [ -n "$file" ] || continue
  case "$file" in
    *.kt) ;;
    *) continue ;;
  esac
  is_scanned_source_set "$file" || continue
  if is_excluded "$file"; then
    continue
  fi

  hit="$(awk "$CHECK_AWK" "$file")"
  if [ -n "$hit" ]; then
    printf '%s\n' "$hit"
    hits=$(printf '%s\n' "$hit" | wc -l | tr -d ' ')
    violations=$((violations + hits))
  fi
done < <(printf '%s\n' "$files")

if [ "$violations" -gt 0 ]; then
  echo
  echo "check-user-facing-strings: $violations hardcoded ShowSnackbar message(s); see CODING_STANDARDS.md" >&2
  exit 1
fi

exit 0
