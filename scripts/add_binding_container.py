#!/usr/bin/env python3
"""
Follow-up to migrate_annotations_to_metro.py: add @BindingContainer to every
`object` that has @ContributesTo(AppScope::class) on it. Metro requires
@Provides to live inside a @BindingContainer-annotated class/object (or an
interface, or a class companion), not directly on a plain `object`.

Idempotent.
"""
from __future__ import annotations

import re
import subprocess
from pathlib import Path

METRO_IMPORT = "import dev.zacsweers.metro.BindingContainer"


def process(path: Path) -> bool:
    text = path.read_text()
    if "@ContributesTo(AppScope::class)" not in text:
        return False
    if "@BindingContainer" in text:
        return False

    lines = text.splitlines()
    out: list[str] = []
    inserted = False
    for line in lines:
        # Look for `object <Name>` lines whose preceding non-blank lines include
        # `@ContributesTo(AppScope::class)`.
        if re.match(r"^object \w+", line.strip()):
            # Walk backward past contiguous annotation lines.
            ann_start = len(out)
            while ann_start > 0 and out[ann_start - 1].strip().startswith("@"):
                ann_start -= 1
            window = out[ann_start:]
            if any("@ContributesTo(AppScope::class)" in w for w in window) and not any(
                "@BindingContainer" in w for w in window
            ):
                out.insert(ann_start, "@BindingContainer")
                inserted = True
        out.append(line)

    if not inserted:
        return False

    # Add the import.
    if METRO_IMPORT not in out:
        idxs = [i for i, l in enumerate(out) if l.startswith("import ")]
        if idxs:
            start, end = idxs[0], idxs[-1] + 1
            block = sorted(set(out[start:end]) | {METRO_IMPORT})
            out = out[:start] + block + out[end:]

    new_text = "\n".join(out)
    if text.endswith("\n"):
        new_text += "\n"
    if new_text == text:
        return False
    path.write_text(new_text)
    return True


def main() -> int:
    root = Path("/home/sigma/projects/android/chipbox")
    grep = subprocess.check_output(
        ["grep", "-rln", "--include=*.kt", "@ContributesTo(AppScope::class)", "."],
        cwd=root,
    ).decode().splitlines()
    targets = [t for t in grep if "/build/" not in t]
    changed = 0
    for t in targets:
        p = root / t
        if process(p):
            print(f"updated {t}")
            changed += 1
    print(f"\n{changed} file(s) changed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
