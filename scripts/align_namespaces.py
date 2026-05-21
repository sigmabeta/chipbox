#!/usr/bin/env python3
"""
One-off namespace alignment for chipbox modules whose declared `namespace =` strings
predate the strict `chipboxNamespace()` rule (drop cbox + android segments, keep all the rest).

For each module in MODULES:
  1. Move source files from <module>/src/<srcroot>/<old-pkg-dir>/ to <module>/src/<srcroot>/<new-pkg-dir>/.
  2. Rewrite `package <old-pkg>` declarations in moved files.
  3. Rewrite the module's build.gradle.kts to call chipboxNamespace() instead of hardcoding.
  4. Across the whole chipbox tree (excluding build/, snapshots, and .git), rewrite imports
     and FQCN references that target the old package.

Idempotent: rerunning is a no-op because the negative-lookahead avoids double-rewriting
already-aligned references.

Kept in scripts/ for reference; not part of the normal build.
"""

from __future__ import annotations
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# (module_dir, old_pkg, new_pkg)
# old/new are dotted Java package paths.
MODULES: list[tuple[str, str, str]] = [
    ("cbox/android/appui/api",            "net.sigmabeta.chipbox.appui",          "net.sigmabeta.chipbox.appui.api"),
    ("cbox/android/artworkprovider/api",  "net.sigmabeta.chipbox.artwork",        "net.sigmabeta.chipbox.artworkprovider.api"),
    ("cbox/android/colors/api",           "net.sigmabeta.chipbox.colors",         "net.sigmabeta.chipbox.colors.api"),
    ("cbox/android/coroutines/api",       "net.sigmabeta.chipbox.coroutines",     "net.sigmabeta.chipbox.coroutines.api"),
    ("cbox/android/image-loading/api",    "net.sigmabeta.chipbox.image_loading",  "net.sigmabeta.chipbox.imageloading.api"),
    ("cbox/android/images/api",           "net.sigmabeta.chipbox.images",         "net.sigmabeta.chipbox.images.api"),
    ("cbox/android/player-status/api",    "net.sigmabeta.chipbox.playerstatus",   "net.sigmabeta.chipbox.playerstatus.api"),
    ("cbox/android/services/api",         "net.sigmabeta.chipbox.services",       "net.sigmabeta.chipbox.services.api"),
    ("cbox/android/storage/api",          "net.sigmabeta.chipbox.storage",        "net.sigmabeta.chipbox.storage.api"),
    ("cbox/android/strings/api",          "net.sigmabeta.chipbox.strings",        "net.sigmabeta.chipbox.strings.api"),
    ("cbox/android/ui/chrome/api",        "net.sigmabeta.chipbox.ui.chrome",      "net.sigmabeta.chipbox.ui.chrome.api"),
    ("cbox/android/ui/components/api",    "net.sigmabeta.chipbox.ui.components",  "net.sigmabeta.chipbox.ui.components.api"),
    ("cbox/android/ui/freeform/api",      "net.sigmabeta.chipbox.ui.freeform",    "net.sigmabeta.chipbox.ui.freeform.api"),
    ("cbox/android/ui/list/api",          "net.sigmabeta.chipbox.ui.list",        "net.sigmabeta.chipbox.ui.list.api"),
    ("cbox/android/ui/theme/api",         "net.sigmabeta.chipbox.ui.theme",       "net.sigmabeta.chipbox.ui.theme.api"),
]

SRC_ROOTS = [
    "src/main/java",
    "src/main/kotlin",
    "src/test/java",
    "src/test/kotlin",
    "src/androidMain/kotlin",
    "src/androidMain/java",
    "src/androidUnitTest/kotlin",
    "src/androidInstrumentedTest/kotlin",
    "src/commonMain/kotlin",
    "src/commonTest/kotlin",
    "src/jvmMain/kotlin",
    "src/jvmMain/java",
    "src/jvmTest/kotlin",
    "src/jvmSharedMain/kotlin",
    "src/jvmSharedMain/java",
]

REWRITE_EXTS = {".kt", ".java", ".kts", ".xml", ".md", ".txt"}


def run(cmd: list[str], cwd: Path | None = None) -> str:
    result = subprocess.run(cmd, cwd=cwd or REPO, capture_output=True, text=True, check=True)
    return result.stdout


def pkg_to_path(pkg: str) -> str:
    return pkg.replace(".", "/")


def move_module_sources(module: str, old_pkg: str, new_pkg: str) -> int:
    """Move files from src/*/<old_pkg_path>/ to src/*/<new_pkg_path>/ via git mv. Returns count.

    Snapshots the source dir's children before creating the target dir, since when new_pkg is
    a sub-package of old_pkg (e.g. chipbox.colors → chipbox.colors.api), the target is a
    subdirectory of the source. Without a snapshot we'd recurse into the dir we just created.
    """
    moved = 0
    old_path_frag = pkg_to_path(old_pkg)
    new_path_frag = pkg_to_path(new_pkg)
    mod_root = REPO / module
    for root in SRC_ROOTS:
        old_dir = mod_root / root / old_path_frag
        if not old_dir.is_dir():
            continue
        new_dir = mod_root / root / new_path_frag
        children = sorted(old_dir.iterdir())
        new_dir.mkdir(parents=True, exist_ok=True)
        for child in children:
            if child.resolve() == new_dir.resolve():
                # new_dir is a subdir of old_dir — skip iterating into it.
                continue
            target = new_dir / child.name
            run(["git", "mv", str(child.relative_to(REPO)), str(target.relative_to(REPO))])
            moved += 1
        # Remove the now-empty old dir (best-effort).
        try:
            old_dir.rmdir()
        except OSError:
            pass
    return moved


def rewrite_package_decls(module: str, old_pkg: str, new_pkg: str) -> int:
    """Rewrite `package <old>` → `package <new>` in the module's source files.

    Uses a negative lookahead so reruns don't keep appending ".api": `package <old>` only
    rewrites if the bit following <old> isn't already what we'd be inserting.
    """
    count = 0
    mod_root = REPO / module
    if new_pkg.startswith(old_pkg + "."):
        tail = new_pkg[len(old_pkg):]  # leading dot included
        guard = r"(?!" + re.escape(tail) + r")"
    else:
        guard = ""
    pattern = re.compile(
        r"^(\s*package\s+)" + re.escape(old_pkg) + r"\b" + guard,
        re.MULTILINE,
    )
    for path in mod_root.rglob("*"):
        if not path.is_file() or path.suffix not in {".kt", ".java"}:
            continue
        if "/build/" in str(path):
            continue
        text = path.read_text()
        new_text, n = pattern.subn(r"\1" + new_pkg, text)
        if n:
            path.write_text(new_text)
            count += n
    return count


def rewrite_build_file(module: str, new_pkg: str) -> bool:
    """Replace the module's hardcoded namespace string with chipboxNamespace().

    Adds an `import net.sigmabeta.sage.plugins.components.chipboxNamespace` line at the top
    of the build file if not already present.
    """
    build_file = REPO / module / "build.gradle.kts"
    if not build_file.exists():
        return False
    text = build_file.read_text()
    quoted_old = re.compile(r'namespace\s*=\s*"net\.sigmabeta\.chipbox\.[^"]*"')
    new_text, n = quoted_old.subn("namespace = chipboxNamespace()", text)
    if n == 0:
        return False
    import_line = "import net.sigmabeta.sage.plugins.components.chipboxNamespace\n"
    if "net.sigmabeta.sage.plugins.components.chipboxNamespace" not in new_text:
        new_text = import_line + "\n" + new_text
    build_file.write_text(new_text)
    return True


def rewrite_repo_references(rewrites: list[tuple[str, str]]) -> int:
    """For every (old_pkg, new_pkg), rewrite references in all .kt/.java/.kts/.xml/.md/.txt files.

    Uses a negative lookahead to avoid double-rewriting. For example, `chipbox.colors` only
    rewrites to `chipbox.colors.api` if not already followed by `.api`.
    """
    patterns: list[tuple[re.Pattern[str], str]] = []
    for old_pkg, new_pkg in rewrites:
        # Determine the "tail" of the new package relative to the old: the bits to insert.
        # e.g. colors → colors.api  ⇒ tail is ".api"
        # e.g. artwork → artworkprovider.api ⇒ this is a full rename, no shared tail; handled separately.
        if new_pkg.startswith(old_pkg + "."):
            tail = new_pkg[len(old_pkg):]  # leading dot included
            # Match old_pkg not already followed by the new tail.
            pat = re.compile(
                r"\b" + re.escape(old_pkg) + r"\b(?!" + re.escape(tail) + r")"
            )
            patterns.append((pat, new_pkg))
        else:
            # Full rename (e.g. chipbox.artwork → chipbox.artworkprovider.api).
            # Negative lookahead checks we don't accidentally rewrite the new form back.
            pat = re.compile(r"\b" + re.escape(old_pkg) + r"\b")
            patterns.append((pat, new_pkg))

    edits = 0
    skip_dirs = {"build", ".git", ".gradle", "ktlint", "node_modules"}
    for root, dirs, files in os.walk(REPO):
        dirs[:] = [d for d in dirs if d not in skip_dirs and not d.endswith(".snapshots")]
        # Skip Paparazzi snapshot dirs anywhere under features/*/screenshot.
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
    rewrites = [(old, new) for _, old, new in MODULES]

    for module, old_pkg, new_pkg in MODULES:
        print(f"\n=== {module} ===")
        print(f"  {old_pkg} → {new_pkg}")
        if not (REPO / module).is_dir():
            print(f"  (skip: missing)")
            continue
        moved = move_module_sources(module, old_pkg, new_pkg)
        print(f"  moved {moved} file/dir entries")
        pkg_edits = rewrite_package_decls(module, old_pkg, new_pkg)
        print(f"  rewrote {pkg_edits} package declarations")
        build_changed = rewrite_build_file(module, new_pkg)
        print(f"  build.gradle.kts updated: {build_changed}")

    print("\n=== repo-wide reference rewrite ===")
    edits = rewrite_repo_references(rewrites)
    print(f"  touched {edits} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
