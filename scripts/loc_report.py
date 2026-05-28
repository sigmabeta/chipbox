#!/usr/bin/env python3
"""Report Kotlin LOC for the chipbox project.

Two views:
  1) Per-module, broken down by sourceSet (commonMain, jvmMain, androidMain, ...).
  2) Per module type (kmp / android / jvm), aggregated across all modules.

Module type is derived from plugins declared in each module's build.gradle.kts.
LOC counts include all `.kt` files under each sourceSet directory; both raw
and code-only (non-blank, non-comment) counts are reported.

Usage:
    scripts/loc_report.py                       # full report
    scripts/loc_report.py --root <path>         # alternate project root
    scripts/loc_report.py --exclude-sage        # skip the sage/ included build
    scripts/loc_report.py --json                # emit JSON instead of tables
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

# Plugin id -> module type. First match wins, but we prefer KMP if any KMP
# plugin is present (some KMP convention plugins also pull in android extensions).
KMP_PLUGINS = {
    "sage.kmp",
    "sage.compose.kmp",
    "chipbox.feature.api",
    "chipbox.feature.real",
    "chipbox.emulator.real",
    "kotlin.multiplatform",
}
ANDROID_PLUGINS = {
    "sage.android",
    "sage.compose.android",
    "sage.di.worker.android",
    "chipbox.screenshot",
    "chipbox.emulator.native",
    "android.application",
    "android.library",
    "android.test",
}
JVM_PLUGINS = {
    "sage.jvm",
    "sage.di",
    "kotlin.jvm",
    "kotlin-dsl",
}

# Plugins that don't determine module type on their own (additive markers).
IGNORED_PLUGINS = {
    "chipbox.kmp.test",
    "kotlin.serialization",
    "compose.compiler",
    "compose.multiplatform",
    "metro",
    "ksp",
    "ktlint",
    "detekt",
}

# Directories to skip when walking the project tree.
SKIP_DIRS = {
    "build",
    ".gradle",
    ".kotlin",
    "node_modules",
    "kotlin-js-store",
    ".git",
    ".idea",
}

# Recognized Kotlin sourceSet directory names (immediate children of `src/`).
# Anything else we still count if it contains .kt files, but flag for visibility.
KNOWN_SOURCE_SETS = {
    "commonMain", "commonTest",
    "jvmMain", "jvmTest",
    "jvmSharedMain", "jvmSharedTest",
    "androidMain", "androidUnitTest", "androidInstrumentedTest",
    "androidDebug", "androidRelease",
    "jsMain", "jsTest",
    "nativeMain", "nativeTest",
    "main", "test",
}

PLUGIN_ID_RE = re.compile(r'id\(\s*"([^"]+)"\s*\)')
PLUGIN_ALIAS_RE = re.compile(r'alias\(\s*libs\.plugins\.([A-Za-z0-9_.]+)\s*\)')
PLUGIN_BACKTICK_RE = re.compile(r'`([A-Za-z0-9_.\-]+)`')
PLUGIN_KOTLIN_RE = re.compile(r'kotlin\(\s*"([^"]+)"\s*\)')


@dataclass
class SourceSetStats:
    files: int = 0
    raw_lines: int = 0
    code_lines: int = 0


@dataclass
class ModuleStats:
    path: str            # gradle-style path, e.g. ":features:home:real"
    rel_dir: str         # filesystem path relative to project root
    module_type: str     # "kmp" | "android" | "jvm" | "unknown"
    plugins: list[str] = field(default_factory=list)
    source_sets: dict[str, SourceSetStats] = field(default_factory=dict)

    @property
    def total(self) -> SourceSetStats:
        agg = SourceSetStats()
        for ss in self.source_sets.values():
            agg.files += ss.files
            agg.raw_lines += ss.raw_lines
            agg.code_lines += ss.code_lines
        return agg


def parse_plugins(build_file: Path) -> list[str]:
    """Extract plugin identifiers from a build.gradle.kts plugins {} block."""
    text = build_file.read_text(encoding="utf-8", errors="replace")
    # Crudely locate the first `plugins {` block. Build files in this project
    # only declare one such block.
    start = text.find("plugins {")
    if start == -1:
        return []
    depth = 0
    end = start
    for i in range(start, len(text)):
        ch = text[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    block = text[start:end]

    plugins: list[str] = []
    for m in PLUGIN_ID_RE.finditer(block):
        plugins.append(m.group(1))
    for m in PLUGIN_ALIAS_RE.finditer(block):
        # Alias keys are dot-separated, mirror the version-catalog name.
        plugins.append(m.group(1))
    for m in PLUGIN_BACKTICK_RE.finditer(block):
        plugins.append(m.group(1))
    for m in PLUGIN_KOTLIN_RE.finditer(block):
        # kotlin("jvm") -> kotlin.jvm; kotlin("multiplatform") -> kotlin.multiplatform.
        plugins.append("kotlin." + m.group(1))
    return plugins


def classify(plugins: list[str]) -> str:
    plugin_set = {p for p in plugins if p not in IGNORED_PLUGINS}
    if plugin_set & KMP_PLUGINS:
        return "kmp"
    if plugin_set & ANDROID_PLUGINS:
        return "android"
    if plugin_set & JVM_PLUGINS:
        return "jvm"
    return "unknown"


def count_kotlin_file(path: Path) -> tuple[int, int]:
    """Return (raw_lines, code_lines) for a .kt file.

    code_lines = non-blank lines that aren't pure single-line `//` comments or
    inside a `/* ... */` block. This is a simple heuristic, not a full lexer.
    """
    raw = 0
    code = 0
    in_block = False
    try:
        with path.open("r", encoding="utf-8", errors="replace") as f:
            for line in f:
                raw += 1
                s = line.strip()
                if not s:
                    continue
                if in_block:
                    if "*/" in s:
                        in_block = False
                        after = s.split("*/", 1)[1].strip()
                        if after and not after.startswith("//"):
                            code += 1
                    continue
                if s.startswith("//"):
                    continue
                if s.startswith("/*"):
                    if "*/" in s[2:]:
                        # Single-line block comment; check for trailing code.
                        after = s.split("*/", 1)[1].strip()
                        if after and not after.startswith("//"):
                            code += 1
                    else:
                        in_block = True
                    continue
                code += 1
    except OSError:
        pass
    return raw, code


def iter_modules(root: Path, exclude_sage: bool) -> list[Path]:
    """Find every directory containing a build.gradle.kts (a Gradle module)."""
    modules: list[Path] = []
    skip_extra: set[str] = set()
    if exclude_sage:
        skip_extra = {"sage"}
    for dirpath, dirnames, filenames in os.walk(root):
        # Prune.
        dirnames[:] = [
            d for d in dirnames
            if d not in SKIP_DIRS and not (
                exclude_sage and Path(dirpath).resolve() == root.resolve() and d in skip_extra
            )
        ]
        if "build.gradle.kts" in filenames:
            modules.append(Path(dirpath))
    return modules


def module_gradle_path(root: Path, module_dir: Path) -> str:
    rel = module_dir.resolve().relative_to(root.resolve())
    parts = rel.parts
    if not parts:
        return ":"
    return ":" + ":".join(parts)


def scan_source_sets(module_dir: Path) -> dict[str, SourceSetStats]:
    """Walk `src/<sourceSet>/...` for the module, counting .kt files per sourceSet."""
    src = module_dir / "src"
    out: dict[str, SourceSetStats] = {}
    if not src.is_dir():
        return out
    for entry in sorted(src.iterdir()):
        if not entry.is_dir():
            continue
        stats = SourceSetStats()
        for kt in entry.rglob("*.kt"):
            if any(part in SKIP_DIRS for part in kt.parts):
                continue
            raw, code = count_kotlin_file(kt)
            stats.files += 1
            stats.raw_lines += raw
            stats.code_lines += code
        if stats.files > 0:
            out[entry.name] = stats
    return out


def collect(root: Path, exclude_sage: bool) -> list[ModuleStats]:
    results: list[ModuleStats] = []
    for module_dir in iter_modules(root, exclude_sage):
        build_file = module_dir / "build.gradle.kts"
        plugins = parse_plugins(build_file)
        mtype = classify(plugins)
        source_sets = scan_source_sets(module_dir)
        if not plugins and not source_sets:
            # Empty aggregator module (e.g. root build.gradle.kts).
            continue
        results.append(ModuleStats(
            path=module_gradle_path(root, module_dir),
            rel_dir=str(module_dir.resolve().relative_to(root.resolve())) or ".",
            module_type=mtype,
            plugins=plugins,
            source_sets=source_sets,
        ))
    return results


# ---- rendering ----------------------------------------------------------------

def fmt_int(n: int) -> str:
    return f"{n:,}"


def fmt_pct(numer: int, denom: int) -> str:
    if denom <= 0:
        return "    -"
    return f"{(100.0 * numer / denom):5.1f}%"


def render_by_module(results: list[ModuleStats]) -> str:
    # Sort by total code lines descending.
    results_sorted = sorted(
        (r for r in results if r.total.files > 0),
        key=lambda r: r.total.code_lines,
        reverse=True,
    )
    if not results_sorted:
        return "(no Kotlin sources found)"
    grand_code = sum(r.total.code_lines for r in results_sorted)
    lines: list[str] = []
    # Percent columns:
    #   sourceSet rows -> % of module's code (intra-module distribution)
    #   module-total rows -> % of grand-total code (module's share of project)
    header = (
        f"{'Module':<60} {'Type':<8} {'SourceSet':<22} "
        f"{'Files':>7} {'Raw':>10} {'Code':>10} {'%Mod':>6} {'%All':>6}"
    )
    sep = "-" * len(header)
    lines.append(header)
    lines.append(sep)
    for m in results_sorted:
        first = True
        t = m.total
        for name in sorted(m.source_sets.keys(), key=lambda n: (-m.source_sets[n].code_lines, n)):
            ss = m.source_sets[name]
            tag = "" if name in KNOWN_SOURCE_SETS else " (?)"
            lines.append(
                f"{(m.path if first else ''):<60} "
                f"{(m.module_type if first else ''):<8} "
                f"{(name + tag):<22} "
                f"{fmt_int(ss.files):>7} "
                f"{fmt_int(ss.raw_lines):>10} "
                f"{fmt_int(ss.code_lines):>10} "
                f"{fmt_pct(ss.code_lines, t.code_lines):>6} "
                f"{fmt_pct(ss.code_lines, grand_code):>6}"
            )
            first = False
        lines.append(
            f"{'':<60} {'':<8} {'  -> module total':<22} "
            f"{fmt_int(t.files):>7} {fmt_int(t.raw_lines):>10} {fmt_int(t.code_lines):>10} "
            f"{'100.0%':>6} {fmt_pct(t.code_lines, grand_code):>6}"
        )
        lines.append(sep)
    return "\n".join(lines)


def render_by_module_type(results: list[ModuleStats]) -> str:
    by_type: dict[str, SourceSetStats] = defaultdict(SourceSetStats)
    by_type_modules: dict[str, int] = defaultdict(int)
    by_type_source_sets: dict[str, dict[str, SourceSetStats]] = defaultdict(lambda: defaultdict(SourceSetStats))

    for m in results:
        if m.total.files == 0:
            continue
        by_type_modules[m.module_type] += 1
        agg = by_type[m.module_type]
        agg.files += m.total.files
        agg.raw_lines += m.total.raw_lines
        agg.code_lines += m.total.code_lines
        for name, ss in m.source_sets.items():
            sagg = by_type_source_sets[m.module_type][name]
            sagg.files += ss.files
            sagg.raw_lines += ss.raw_lines
            sagg.code_lines += ss.code_lines

    if not by_type:
        return "(no Kotlin sources found)"

    grand = SourceSetStats()
    for t in by_type.values():
        grand.files += t.files
        grand.raw_lines += t.raw_lines
        grand.code_lines += t.code_lines

    lines: list[str] = []
    # Percent columns:
    #   sourceSet rows -> % of that type's code (intra-type distribution)
    #   type-total rows -> % of grand-total code (type's share of project)
    header = (
        f"{'Type':<10} {'Modules':>8} {'SourceSet':<22} "
        f"{'Files':>7} {'Raw':>10} {'Code':>10} {'%Type':>6} {'%All':>6}"
    )
    sep = "-" * len(header)
    lines.append(header)
    lines.append(sep)

    order = ["kmp", "android", "jvm", "unknown"]
    for t in order:
        if t not in by_type:
            continue
        first = True
        total = by_type[t]
        for name in sorted(by_type_source_sets[t].keys(), key=lambda n: (-by_type_source_sets[t][n].code_lines, n)):
            ss = by_type_source_sets[t][name]
            lines.append(
                f"{(t if first else ''):<10} "
                f"{(fmt_int(by_type_modules[t]) if first else ''):>8} "
                f"{name:<22} "
                f"{fmt_int(ss.files):>7} "
                f"{fmt_int(ss.raw_lines):>10} "
                f"{fmt_int(ss.code_lines):>10} "
                f"{fmt_pct(ss.code_lines, total.code_lines):>6} "
                f"{fmt_pct(ss.code_lines, grand.code_lines):>6}"
            )
            first = False
        lines.append(
            f"{'':<10} {'':>8} {'  -> type total':<22} "
            f"{fmt_int(total.files):>7} {fmt_int(total.raw_lines):>10} {fmt_int(total.code_lines):>10} "
            f"{'100.0%':>6} {fmt_pct(total.code_lines, grand.code_lines):>6}"
        )
        lines.append(sep)

    lines.append(
        f"{'GRAND':<10} {fmt_int(sum(by_type_modules.values())):>8} "
        f"{'':<22} {fmt_int(grand.files):>7} {fmt_int(grand.raw_lines):>10} {fmt_int(grand.code_lines):>10} "
        f"{'':>6} {'100.0%':>6}"
    )
    return "\n".join(lines)


def to_json(results: list[ModuleStats]) -> dict:
    return {
        "modules": [
            {
                "path": m.path,
                "rel_dir": m.rel_dir,
                "type": m.module_type,
                "plugins": m.plugins,
                "source_sets": {
                    name: {
                        "files": ss.files,
                        "raw_lines": ss.raw_lines,
                        "code_lines": ss.code_lines,
                    }
                    for name, ss in m.source_sets.items()
                },
            }
            for m in results
        ],
    }


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0] if __doc__ else "")
    p.add_argument("--root", default=None, help="Project root (defaults to script's parent dir).")
    p.add_argument("--exclude-sage", action="store_true",
                   help="Skip the bundled sage/ included build.")
    p.add_argument("--json", action="store_true", help="Emit JSON.")
    args = p.parse_args(argv)

    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parent.parent
    if not (root / "settings.gradle.kts").exists():
        print(f"warning: {root} does not look like a Gradle project root", file=sys.stderr)

    results = collect(root, exclude_sage=args.exclude_sage)

    if args.json:
        json.dump(to_json(results), sys.stdout, indent=2)
        sys.stdout.write("\n")
        return 0

    print(f"# Chipbox Kotlin LOC report (root: {root})")
    if args.exclude_sage:
        print("# sage/ excluded")
    print()
    print("## By module (sourceSet breakdown)")
    print(render_by_module(results))
    print()
    print("## By module type")
    print(render_by_module_type(results))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
