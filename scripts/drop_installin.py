#!/usr/bin/env python3
"""
One-off helper for chipbox Hilt → Metro M6.

For each Kotlin file containing `@InstallIn(SingletonComponent::class)`:
- Drop the `@InstallIn(SingletonComponent::class)` annotation line.
- Drop unused Hilt imports: `dagger.hilt.InstallIn` and
  `dagger.hilt.components.SingletonComponent`.
- Leave `@Module`, `@Provides`, `@Binds`, `@Singleton`, `@ContributesTo`, etc.
  alone — Metro's Dagger interop consumes the @Module/@Provides annotations.

Idempotent.
"""
from __future__ import annotations

import re
import subprocess
from pathlib import Path

DROP_IMPORTS = {
    "import dagger.hilt.InstallIn",
    "import dagger.hilt.components.SingletonComponent",
}


def process(path: Path) -> bool:
    original = path.read_text()
    if "@InstallIn(SingletonComponent::class)" not in original:
        return False
    lines = original.splitlines()
    out: list[str] = []
    for line in lines:
        stripped = line.strip()
        if stripped == "@InstallIn(SingletonComponent::class)":
            continue
        if stripped in DROP_IMPORTS:
            continue
        out.append(line)
    new_text = "\n".join(out)
    if original.endswith("\n"):
        new_text += "\n"
    if new_text == original:
        return False
    path.write_text(new_text)
    return True


def main() -> int:
    root = Path("/home/sigma/projects/android/chipbox")
    targets = subprocess.check_output(
        ["grep", "-rln", "@InstallIn(SingletonComponent::class)", "--include=*.kt", "."],
        cwd=root,
    ).decode().splitlines()
    targets = [t for t in targets if "/build/" not in t]
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
