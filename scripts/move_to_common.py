#!/usr/bin/env python3
"""
Move five KMP modules from cbox/android/ to cbox/common/, with the matching
namespace and source-package shifts:

  cbox/android/appui/api          → cbox/common/appui/api
  cbox/android/player-status/api  → cbox/common/player-status/api
  cbox/android/ui/chrome/api      → cbox/common/ui/chrome/api
  cbox/android/ui/freeform/api    → cbox/common/ui/freeform/api
  cbox/android/ui/list/api        → cbox/common/ui/list/api

Each move involves:
  1. git mv'ing the module directory.
  2. git mv'ing source files under the module from chipbox/<old>/ to chipbox/common/<old>/.
  3. Rewriting `package` declarations in the moved files.
  4. Across the rest of the repo, rewriting:
     - imports / FQCN refs to the moved namespaces
     - typesafe project accessors (`projects.cbox.android.X` → `projects.cbox.common.X`)
     - Gradle path strings (`:cbox:android:X` → `:cbox:common:X`) in settings.gradle.kts

The chipboxNamespace() helper picks up the new path automatically — no build.gradle.kts
edits to the moved modules are needed beyond what the typesafe-project-ref rewrite does.

Idempotent under reruns: the rewritten forms contain `common.` between `chipbox.` and the
moved segment, so the substring patterns can't match a second time.
"""

from __future__ import annotations
import os
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

MOVES = [
    {
        "old_module":   "cbox/android/appui/api",
        "new_module":   "cbox/common/appui/api",
        "old_gradle":   ":cbox:android:appui:api",
        "new_gradle":   ":cbox:common:appui:api",
        "old_typesafe": "projects.cbox.android.appui",
        "new_typesafe": "projects.cbox.common.appui",
        "old_ns":       "net.sigmabeta.chipbox.appui",
        "new_ns":       "net.sigmabeta.chipbox.common.appui",
        "old_pkg_path": "net/sigmabeta/chipbox/appui",
        "new_pkg_path": "net/sigmabeta/chipbox/common/appui",
    },
    {
        "old_module":   "cbox/android/player-status/api",
        "new_module":   "cbox/common/player-status/api",
        "old_gradle":   ":cbox:android:player-status:api",
        "new_gradle":   ":cbox:common:player-status:api",
        "old_typesafe": "projects.cbox.android.playerStatus",
        "new_typesafe": "projects.cbox.common.playerStatus",
        "old_ns":       "net.sigmabeta.chipbox.playerstatus",
        "new_ns":       "net.sigmabeta.chipbox.common.playerstatus",
        "old_pkg_path": "net/sigmabeta/chipbox/playerstatus",
        "new_pkg_path": "net/sigmabeta/chipbox/common/playerstatus",
    },
    {
        "old_module":   "cbox/android/ui/chrome/api",
        "new_module":   "cbox/common/ui/chrome/api",
        "old_gradle":   ":cbox:android:ui:chrome:api",
        "new_gradle":   ":cbox:common:ui:chrome:api",
        "old_typesafe": "projects.cbox.android.ui.chrome",
        "new_typesafe": "projects.cbox.common.ui.chrome",
        "old_ns":       "net.sigmabeta.chipbox.ui.chrome",
        "new_ns":       "net.sigmabeta.chipbox.common.ui.chrome",
        "old_pkg_path": "net/sigmabeta/chipbox/ui/chrome",
        "new_pkg_path": "net/sigmabeta/chipbox/common/ui/chrome",
    },
    {
        "old_module":   "cbox/android/ui/freeform/api",
        "new_module":   "cbox/common/ui/freeform/api",
        "old_gradle":   ":cbox:android:ui:freeform:api",
        "new_gradle":   ":cbox:common:ui:freeform:api",
        "old_typesafe": "projects.cbox.android.ui.freeform",
        "new_typesafe": "projects.cbox.common.ui.freeform",
        "old_ns":       "net.sigmabeta.chipbox.ui.freeform",
        "new_ns":       "net.sigmabeta.chipbox.common.ui.freeform",
        "old_pkg_path": "net/sigmabeta/chipbox/ui/freeform",
        "new_pkg_path": "net/sigmabeta/chipbox/common/ui/freeform",
    },
    {
        "old_module":   "cbox/android/ui/list/api",
        "new_module":   "cbox/common/ui/list/api",
        "old_gradle":   ":cbox:android:ui:list:api",
        "new_gradle":   ":cbox:common:ui:list:api",
        "old_typesafe": "projects.cbox.android.ui.list",
        "new_typesafe": "projects.cbox.common.ui.list",
        "old_ns":       "net.sigmabeta.chipbox.ui.list",
        "new_ns":       "net.sigmabeta.chipbox.common.ui.list",
        "old_pkg_path": "net/sigmabeta/chipbox/ui/list",
        "new_pkg_path": "net/sigmabeta/chipbox/common/ui/list",
    },
]

SRC_ROOTS = [
    "src/main/java", "src/main/kotlin",
    "src/test/java", "src/test/kotlin",
    "src/androidMain/kotlin", "src/androidMain/java",
    "src/androidUnitTest/kotlin", "src/androidInstrumentedTest/kotlin",
    "src/commonMain/kotlin", "src/commonTest/kotlin",
    "src/jvmMain/kotlin", "src/jvmMain/java", "src/jvmTest/kotlin",
    "src/jvmSharedMain/kotlin", "src/jvmSharedMain/java",
]
REWRITE_EXTS = {".kt", ".java", ".kts", ".xml", ".md", ".txt"}


def run(cmd: list[str]) -> None:
    subprocess.run(cmd, cwd=REPO, check=True, capture_output=True, text=True)


def move_module_dir(old: str, new: str) -> bool:
    if not (REPO / old).is_dir():
        return False
    (REPO / new).parent.mkdir(parents=True, exist_ok=True)
    run(["git", "mv", old, new])
    return True


def move_source_files(new_module: str, old_pkg_path: str, new_pkg_path: str) -> int:
    moved = 0
    mod_root = REPO / new_module
    for root in SRC_ROOTS:
        old_dir = mod_root / root / old_pkg_path
        if not old_dir.is_dir():
            continue
        new_dir = mod_root / root / new_pkg_path
        children = sorted(old_dir.iterdir())
        new_dir.mkdir(parents=True, exist_ok=True)
        for child in children:
            if child.resolve() == new_dir.resolve():
                continue
            target = new_dir / child.name
            run(["git", "mv", str(child.relative_to(REPO)), str(target.relative_to(REPO))])
            moved += 1
        try:
            old_dir.rmdir()
        except OSError:
            pass
        # Tidy up any now-empty parent dirs up to the src root.
        parent = old_dir.parent
        while parent != mod_root / root:
            try:
                parent.rmdir()
            except OSError:
                break
            parent = parent.parent
    return moved


def rewrite_package_decls(new_module: str, old_ns: str, new_ns: str) -> int:
    count = 0
    mod_root = REPO / new_module
    pattern = re.compile(r"^(\s*package\s+)" + re.escape(old_ns) + r"\b", re.MULTILINE)
    for path in mod_root.rglob("*"):
        if not path.is_file() or path.suffix not in {".kt", ".java"} or "/build/" in str(path):
            continue
        text = path.read_text()
        new_text, n = pattern.subn(r"\1" + new_ns, text)
        if n:
            path.write_text(new_text)
            count += n
    return count


def rewrite_repo_references(moves: list[dict]) -> int:
    patterns: list[tuple[re.Pattern[str], str]] = []
    for m in moves:
        # Fully-qualified namespace refs (imports + FQCN).
        patterns.append((re.compile(r"\b" + re.escape(m["old_ns"]) + r"\b"), m["new_ns"]))
        # Typesafe project accessor in build.gradle.kts.
        patterns.append((re.compile(r"\b" + re.escape(m["old_typesafe"]) + r"\b"), m["new_typesafe"]))
        # Colon-separated Gradle path in settings.gradle.kts.
        patterns.append((re.compile(re.escape(m["old_gradle"]) + r"\b"), m["new_gradle"]))

    edits = 0
    skip_dirs = {"build", ".git", ".gradle", "ktlint", "node_modules"}
    for root, dirs, files in os.walk(REPO):
        dirs[:] = [d for d in dirs if d not in skip_dirs and not d.endswith(".snapshots")]
        if "snapshots" in Path(root).parts:
            continue
        for fname in files:
            ext = Path(fname).suffix
            if ext not in REWRITE_EXTS:
                continue
            path = Path(root) / fname
            try:
                text = path.read_text()
            except (UnicodeDecodeError, PermissionError):
                continue
            new_text = text
            for pat, repl in patterns:
                new_text = pat.sub(repl, new_text)
            if new_text != text:
                path.write_text(new_text)
                edits += 1
    return edits


def main() -> int:
    for m in MOVES:
        print(f"\n=== {m['old_module']} → {m['new_module']} ===")
        moved_dir = move_module_dir(m["old_module"], m["new_module"])
        print(f"  module dir moved: {moved_dir}")
        moved_files = move_source_files(m["new_module"], m["old_pkg_path"], m["new_pkg_path"])
        print(f"  source files moved: {moved_files}")
        pkg_edits = rewrite_package_decls(m["new_module"], m["old_ns"], m["new_ns"])
        print(f"  package decls updated: {pkg_edits}")

    print("\n=== repo-wide reference rewrite ===")
    edits = rewrite_repo_references(MOVES)
    print(f"  touched {edits} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
