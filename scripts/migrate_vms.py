#!/usr/bin/env python3
"""
One-off helper for chipbox Hilt → Metro M5c sweep.

For each (vm_path, route_path, build_path) triple:
- VM: replace @HiltViewModel annotation with
      @ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
      @ViewModelKey
  Swap imports: drop dagger.hilt.android.lifecycle.HiltViewModel, add
  dev.zacsweers.metro.ContributesIntoMap, dev.zacsweers.metro.binding,
  dev.zacsweers.metrox.viewmodel.ViewModelKey, androidx.lifecycle.ViewModel,
  net.sigmabeta.sage.di.AppScope.
- Route: replace `import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`
  with `import dev.zacsweers.metrox.viewmodel.metroViewModel`, and every
  `hiltViewModel(` call-site → `metroViewModel(`.
- build.gradle.kts: swap
  `implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)` →
  `implementation(libs.metrox.viewmodel)`
  `implementation(libs.metrox.viewmodel.compose)`

Idempotent.
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path("/home/sigma/projects/android/chipbox")

MODULES = [
    "features/artist-detail/real",
    "features/browse-all-tracks/real",
    "features/browse-by-artist/real",
    "features/browse-by-game/real",
    "features/browse-by-platform/real",
    "features/game-detail/real",
    "features/games-for-platform/real",
    "features/library/real",
    "features/now-playing/real",
    "features/playback-status/real",
    "features/search/real",
    "cbox/android/appui/api",
    "cbox/android/player-status/api",
]

VM_IMPORTS_TO_ADD = [
    "import androidx.lifecycle.ViewModel",
    "import dev.zacsweers.metro.ContributesIntoMap",
    "import dev.zacsweers.metro.binding",
    "import dev.zacsweers.metrox.viewmodel.ViewModelKey",
    "import net.sigmabeta.sage.di.AppScope",
]

VM_IMPORTS_TO_DROP = {"import dagger.hilt.android.lifecycle.HiltViewModel"}

ROUTE_IMPORT_OLD = "import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel"
ROUTE_IMPORT_NEW = "import dev.zacsweers.metrox.viewmodel.metroViewModel"


def add_imports(lines: list[str], new_imports: list[str]) -> list[str]:
    existing = {l.strip() for l in lines if l.startswith("import ")}
    to_add = [i for i in new_imports if i not in existing]
    if not to_add:
        return lines
    import_indices = [i for i, l in enumerate(lines) if l.startswith("import ")]
    if not import_indices:
        return lines
    block_start, block_end = import_indices[0], import_indices[-1] + 1
    merged = sorted(set(lines[block_start:block_end]) | set(to_add))
    return lines[:block_start] + merged + lines[block_end:]


def drop_imports(lines: list[str], drop: set[str]) -> list[str]:
    return [l for l in lines if l.strip() not in drop]


def convert_vm(path: Path) -> bool:
    original = path.read_text()
    if "@HiltViewModel" not in original:
        return False
    lines = original.splitlines()
    # Drop @HiltViewModel annotation line — assume it's on its own line above the class.
    lines = [l for l in lines if l.strip() != "@HiltViewModel"]
    # Find the `class <Name>ViewModel` declaration; insert Metro annotations above it.
    class_re = re.compile(r"^(public\s+|internal\s+)?class\s+\w+ViewModel\b")
    out: list[str] = []
    inserted = False
    for line in lines:
        if not inserted and class_re.match(line.strip()):
            # Backtrack past any annotation lines we've already produced so the new
            # ones go above the whole annotation block.
            insertion_point = len(out)
            while insertion_point > 0 and out[insertion_point - 1].strip().startswith("@"):
                insertion_point -= 1
            new = [
                "@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())",
                "@ViewModelKey",
            ]
            out = out[:insertion_point] + new + out[insertion_point:]
            inserted = True
        out.append(line)
    if not inserted:
        raise RuntimeError(f"Could not find class declaration in {path}")
    out = drop_imports(out, VM_IMPORTS_TO_DROP)
    out = add_imports(out, VM_IMPORTS_TO_ADD)
    new_text = "\n".join(out)
    if original.endswith("\n"):
        new_text += "\n"
    if new_text == original:
        return False
    path.write_text(new_text)
    return True


def convert_route(path: Path) -> bool:
    original = path.read_text()
    if "hiltViewModel" not in original:
        return False
    text = original
    text = text.replace(ROUTE_IMPORT_OLD, ROUTE_IMPORT_NEW)
    text = re.sub(r"\bhiltViewModel\b", "metroViewModel", text)
    if text == original:
        return False
    path.write_text(text)
    return True


def convert_build(path: Path) -> bool:
    original = path.read_text()
    old = "    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)"
    if old not in original:
        return False
    new = (
        "    implementation(libs.metrox.viewmodel)\n"
        "    implementation(libs.metrox.viewmodel.compose)"
    )
    text = original.replace(old, new)
    if text == original:
        return False
    path.write_text(text)
    return True


def find_in_module(module_dir: Path, suffix: str) -> Path | None:
    for kt in module_dir.rglob("*.kt"):
        if "/build/" in str(kt):
            continue
        if kt.name.endswith(suffix):
            return kt
    return None


def main() -> int:
    for m in MODULES:
        module_dir = ROOT / m
        vm = find_in_module(module_dir, "ViewModel.kt")
        # Route or Screen (ChipboxAppUi.kt + PlayerStatus.kt are screen composables)
        route = (
            find_in_module(module_dir, "Route.kt")
            or find_in_module(module_dir, "Screen.kt")
            or None
        )
        if route is None:
            # cbox/android/appui/api uses ChipboxAppUi.kt; player-status uses PlayerStatus.kt
            kts = [
                kt for kt in module_dir.rglob("*.kt")
                if "/build/" not in str(kt) and "hiltViewModel" in kt.read_text()
            ]
            if kts:
                route = kts[0]
        build = module_dir / "build.gradle.kts"

        print(f"=== {m}")
        print(f"  vm:    {vm}")
        print(f"  route: {route}")
        print(f"  build: {build}")

        if vm and convert_vm(vm):
            print("    + vm converted")
        if route and convert_route(route):
            print("    + route converted")
        if build.exists() and convert_build(build):
            print("    + build deps swapped")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
