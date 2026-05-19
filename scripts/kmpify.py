#!/usr/bin/env python3
"""One-off helper for the KMP mass conversion (roadmap item 2).

Rewrites a module's build.gradle.kts from sage.android or sage.jvm to sage.kmp:
- plugin alias -> libs.plugins.sage.kmp
- android { namespace = "X" }  -> kotlin { androidLibrary { namespace = "X" } }
- top-level dependencies { ... } -> kotlin { sourceSets { named("jvmSharedMain")
  { dependencies { ... } } } }

For sage.jvm modules (no namespace in the build file) the namespace is taken
from the dominant `package` declaration in the module's Kotlin sources, or
synthesised from the module path if the module has no sources.

Idempotent-ish: refuses to touch a file already on sage.kmp.
"""
import re
import sys
from pathlib import Path

CHIP = "net.sigmabeta.chipbox"


def module_dir(build_file: Path) -> Path:
    return build_file.parent


def find_namespace(text: str) -> str | None:
    m = re.search(r'namespace\s*=\s*"([^"]+)"', text)
    return m.group(1) if m else None


def source_package(mod: Path) -> str | None:
    pkgs: dict[str, int] = {}
    for kt in mod.rglob("src/**/*.kt"):
        for line in kt.read_text().splitlines():
            line = line.strip()
            if line.startswith("package "):
                pkgs[line[len("package "):].strip()] = pkgs.get(
                    line[len("package "):].strip(), 0) + 1
                break
    if not pkgs:
        return None
    # shortest package that is a prefix of all, else the most common one
    return sorted(pkgs, key=lambda p: (len(p), -pkgs[p]))[0]


def path_namespace(mod: Path, repo: Path) -> str:
    # Keep the cbox sub-tree segment (common/android/jvm): the existing
    # sage.android modules use un-prefixed namespaces (e.g.
    # net.sigmabeta.chipbox.strings), so a parallel common module must keep
    # its `common` segment to stay unique and not collide in the manifest
    # merge. Namespace only feeds generated R/BuildConfig — it need not match
    # the source package, only be globally unique and a valid package.
    rel = mod.relative_to(repo).as_posix()
    rel = re.sub(r"^cbox/", "", rel)
    seg = [s for s in re.split(r"[/\-]", rel) if s]
    return CHIP + "." + ".".join(seg)


def extract_deps_block(text: str) -> str | None:
    i = text.find("\ndependencies {")
    if i == -1:
        return None
    depth = 0
    start = text.index("{", i)
    for j in range(start, len(text)):
        if text[j] == "{":
            depth += 1
        elif text[j] == "}":
            depth -= 1
            if depth == 0:
                return text[start + 1:j].strip("\n")
    return None


def reindent(block: str, spaces: int) -> str:
    pad = " " * spaces
    out = []
    for ln in block.splitlines():
        s = ln.strip()
        out.append(pad + s if s else "")
    return "\n".join(out)


def convert(build_file: Path, repo: Path) -> str:
    text = build_file.read_text()
    if "libs.plugins.sage.kmp" in text:
        return "skip (already kmp)"
    is_android = "libs.plugins.sage.android" in text
    is_jvm = "libs.plugins.sage.jvm" in text
    if not (is_android or is_jvm):
        return "skip (not android/jvm)"

    mod = module_dir(build_file)
    # sage.android modules keep their existing (already-unique) namespace;
    # sage.jvm modules have none, so derive a unique one from the module path
    # (NOT the source package — a parallel sage.android module may share the
    # package and that collides in the Android manifest merge).
    ns = find_namespace(text) or path_namespace(mod, repo)

    deps = extract_deps_block(text)

    lines = [
        "plugins {",
        "    alias(libs.plugins.sage.kmp)",
        "}",
        "",
        "kotlin {",
        "    androidLibrary {",
        f'        namespace = "{ns}"',
        "    }",
    ]
    if deps:
        lines += [
            "",
            "    sourceSets {",
            '        named("jvmSharedMain") {',
            "            dependencies {",
            reindent(deps, 16),
            "            }",
            "        }",
            "    }",
        ]
    lines.append("}")
    build_file.write_text("\n".join(lines) + "\n")
    return f"ok ns={ns} deps={'yes' if deps else 'no'}"


def main() -> None:
    repo = Path.cwd().resolve()
    for arg in sys.argv[1:]:
        bf = Path(arg).resolve()
        if bf.is_dir():
            bf = bf / "build.gradle.kts"
        print(f"{convert(bf, repo):<40} {arg}")


if __name__ == "__main__":
    main()
