#!/usr/bin/env python3
"""
One-off helper for chipbox Hilt → Metro migration Milestone 6 final sweep.

Replaces every javax.inject / Dagger annotation reference with the Metro-native
equivalent. After this runs the codebase has no `javax.inject.*` or `dagger.*`
imports anywhere in production source — Metro processes everything natively, no
Dagger interop needed.

Mapping:

  import dagger.Module             →  drop (Metro doesn't need @Module marker)
  import dagger.Provides           →  import dev.zacsweers.metro.Provides
  import dagger.Binds              →  import dev.zacsweers.metro.Binds
  import javax.inject.Inject       →  import dev.zacsweers.metro.Inject
  import javax.inject.Singleton    →  import dev.zacsweers.metro.SingleIn  (+ AppScope import)
  import javax.inject.Named        →  import dev.zacsweers.metro.Named
  import javax.inject.Qualifier    →  import dev.zacsweers.metro.Qualifier

  @Module                          →  drop the annotation line entirely
  @Singleton                       →  @SingleIn(AppScope::class)
  (other annotations keep the same name; only the import changes)

Idempotent — re-running on a converted file is a no-op.
"""
from __future__ import annotations

import re
import subprocess
from pathlib import Path

IMPORT_RENAMES = {
    "import dagger.Provides": "import dev.zacsweers.metro.Provides",
    "import dagger.Binds": "import dev.zacsweers.metro.Binds",
    "import javax.inject.Inject": "import dev.zacsweers.metro.Inject",
    "import javax.inject.Named": "import dev.zacsweers.metro.Named",
    "import javax.inject.Qualifier": "import dev.zacsweers.metro.Qualifier",
    "import javax.inject.Singleton": "import dev.zacsweers.metro.SingleIn",
}
IMPORT_DROPS = {
    "import dagger.Module",
}
APPSCOPE_IMPORT = "import net.sigmabeta.sage.di.AppScope"


def reorder_imports(lines: list[str]) -> list[str]:
    """Sort the contiguous import block alphabetically so freshly-rewritten imports land in
    the right ktlint-pleasing order."""
    indices = [i for i, l in enumerate(lines) if l.startswith("import ")]
    if not indices:
        return lines
    start, end = indices[0], indices[-1] + 1
    block = sorted(set(lines[start:end]))
    return lines[:start] + block + lines[end:]


def process(path: Path) -> bool:
    text = path.read_text()
    original = text

    # 1. Annotation replacements (Singleton → SingleIn(AppScope::class), drop @Module).
    needs_appscope = False
    if re.search(r"^@Singleton\b", text, flags=re.MULTILINE) or re.search(
        r"@Singleton\b(?=\s)", text
    ):
        needs_appscope = True
    # Drop standalone-on-its-own-line @Module annotations.
    text = re.sub(r"^@Module\s*\n", "", text, flags=re.MULTILINE)
    # Replace @Singleton → @SingleIn(AppScope::class) — both on its own line and inline.
    text = re.sub(r"@Singleton\b(?!Component)", "@SingleIn(AppScope::class)", text)

    # 2. Import rewrites.
    lines = text.splitlines()
    new_lines: list[str] = []
    for line in lines:
        stripped = line.strip()
        if stripped in IMPORT_DROPS:
            continue
        if stripped in IMPORT_RENAMES:
            new_lines.append(IMPORT_RENAMES[stripped])
            continue
        new_lines.append(line)

    # 3. Add AppScope import if we introduced @SingleIn(AppScope::class) and don't have it.
    if needs_appscope:
        if APPSCOPE_IMPORT not in new_lines:
            new_lines.append(APPSCOPE_IMPORT)

    new_lines = reorder_imports(new_lines)

    new_text = "\n".join(new_lines)
    if original.endswith("\n"):
        new_text += "\n"

    if new_text == original:
        return False
    path.write_text(new_text)
    return True


def main() -> int:
    root = Path("/home/sigma/projects/android/chipbox")
    # Find every .kt that imports something we need to rewrite OR uses @Singleton / @Module.
    grep = subprocess.check_output(
        [
            "grep",
            "-rln",
            "--include=*.kt",
            "-E",
            r"(^|[^\w])(import (dagger\.(Module|Provides|Binds)|javax\.inject\.(Inject|Singleton|Named|Qualifier))|@(Module|Singleton)\b)",
            ".",
        ],
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
