#!/usr/bin/env python3
"""
One-off helper for the Hilt → Metro migration (Milestone 4 sub-slice 4b).

For each Kotlin file passed on argv:
- Insert `import dev.zacsweers.metro.ContributesTo` and
  `import net.sigmabeta.sage.di.AppScope` (only if not already present), in
  alphabetical order relative to surrounding imports.
- Add `@ContributesTo(AppScope::class)` immediately above any line that starts
  with `object`, `abstract class`, or `class` AND whose preceding lines include
  `@Module` (the @Module declaration we want to aggregate).

Idempotent: re-running on an already-converted file is a no-op.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path


METRO_IMPORT = "import dev.zacsweers.metro.ContributesTo"
APPSCOPE_IMPORT = "import net.sigmabeta.sage.di.AppScope"


def add_import(lines: list[str], new_import: str) -> list[str]:
    if any(line.strip() == new_import for line in lines):
        return lines

    # Find the range of contiguous import lines and insert in sorted position.
    import_indices = [i for i, l in enumerate(lines) if l.startswith("import ")]
    if not import_indices:
        # No imports at all — should never happen for a Kotlin file with @Module,
        # but just in case: append right after the package line.
        for i, l in enumerate(lines):
            if l.startswith("package "):
                return lines[: i + 1] + ["", new_import] + lines[i + 1 :]
        return [new_import] + lines

    insert_at = import_indices[-1] + 1
    for i in import_indices:
        if lines[i] > new_import:
            insert_at = i
            break
    return lines[:insert_at] + [new_import] + lines[insert_at:]


def annotate_module(lines: list[str]) -> list[str]:
    # Walk the file tracking a window of preceding non-blank, non-comment lines.
    # When we hit a class / object declaration whose window contains `@Module`,
    # insert `@ContributesTo(AppScope::class)` immediately above it (after the
    # other annotations).
    out: list[str] = []
    annotation_window_start: int | None = None  # output index where the @-block starts

    for raw_line in lines:
        stripped = raw_line.strip()
        if stripped.startswith("@"):
            if annotation_window_start is None:
                annotation_window_start = len(out)
            out.append(raw_line)
            continue

        if re.match(r"^(public\s+|internal\s+)?(abstract\s+)?(class|object)\b", stripped):
            if annotation_window_start is not None:
                window = out[annotation_window_start:]
                has_module = any("@Module" in w for w in window)
                already_added = any("@ContributesTo" in w for w in window)
                if has_module and not already_added:
                    # Indent the new annotation to match the class/object indent
                    # (which is the file's top-level — Kotlin doesn't indent these).
                    out.append("@ContributesTo(AppScope::class)")
            annotation_window_start = None
            out.append(raw_line)
            continue

        if stripped == "":
            # Blank lines reset the annotation window (a blank line breaks the
            # contiguous-annotations block).
            annotation_window_start = None
            out.append(raw_line)
            continue

        # Any other non-annotation, non-class line: reset window.
        annotation_window_start = None
        out.append(raw_line)

    return out


def process(path: Path) -> bool:
    original = path.read_text()
    lines = original.splitlines()

    # Only touch files that actually declare a @Module.
    if not any("@Module" in l for l in lines):
        return False

    lines = annotate_module(lines)

    # Only add imports if we actually added a @ContributesTo.
    if any("@ContributesTo(AppScope::class)" in l for l in lines):
        lines = add_import(lines, METRO_IMPORT)
        lines = add_import(lines, APPSCOPE_IMPORT)

    new_text = "\n".join(lines)
    if not original.endswith("\n"):
        # Preserve missing-final-newline if original lacked it. Otherwise add it.
        pass
    else:
        new_text += "\n"

    if new_text == original:
        return False

    path.write_text(new_text)
    return True


def main(argv: list[str]) -> int:
    changed = 0
    for arg in argv:
        p = Path(arg)
        if process(p):
            changed += 1
            print(f"updated {p}")
    print(f"\n{changed} file(s) changed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
