# Vendored SSF core — `Core/`

`Core/` is a vendored copy of the **Highly Theoretical** Sega Saturn/Dreamcast
sound emulator (`Core/sega.c`, `satsound.c`, `dcsound.c`, `yam.c`, `arm.c`, the
`m68k/`, `c68k/` and `Starscream/` CPU cores). Upstream:

- https://bitbucket.org/losnoco/highly_theoretical.git (`Core/` subtree)

The chipbox JNI glue around it (`Ssf.cpp`, `Ssf.h`, `kotlin-jni.cpp`,
`Ssf_Web.cpp`, `CMakeLists.txt`) is ours and has no upstream counterpart.

## Sync status — checked 2026-06-01 against upstream `2ddfa0e` (2025-10-10)

**We intentionally do not track upstream HEAD.** The vendored copy was taken
from upstream *before* the Oct 2025 `afb889e "Sync with Cog"` commit, and it has
since diverged in our favour. Re-syncing to current upstream would be a net
regression, so it is deliberately skipped.

### Local changes kept (do not let an upstream sync clobber these)

- **Low-pass filter fixes in `yam.c`** — the filter is moved before attenuation,
  clamped to ±0x8000, and given a zero-cutoff guard (`if(!f) s=0`). These are the
  2020 upstream commits `4bad9bd` / `2e73bc5` / `4209287`, which
  `afb889e "Sync with Cog"` later *dropped*. Removing them reintroduces audible
  filter clipping.
- **Portability adaptations** (needed to build for Android/JVM, non-x86):
  - `yam.c`, `c68k/core.h`: x86 `regparm(3)` / `__fastcall` FASTCALL paths
    disabled.
  - `yam.c`: `(unsigned long)` casts on `&YAMSTATE->dynacode` (pointer→int).
  - `sega.c`: `HAVE_SATSOUND` macro replaced with explicit
    `offset_to_satsound != 0` checks.
- **clang-format** applied to the whole tree (see `Core/.clang-format`). This is
  why a raw `diff` against upstream looks enormous; ~99% is reformatting.

### Upstream-only changes we deliberately skipped

All originate from `afb889e "Sync with Cog"` and are cosmetic — no functional
gain:

- `m68k/m68kops.c`: `(unsigned int)` warning-silencing casts (26 sites).
- `yam.c`: 2 `(sint32)` casts.
- `arm.c`, `m68k/m68kcpu.c`, `m68k/m68kcpu.h`: `#if 0` guards around dead/unused
  functions, `#undef` macro-redefinition guards, `__aarch64__` fastcall handling
  (we already solve the fastcall problem our own way, above).

### If you do re-sync in the future

Do **not** copy `Core/` over wholesale. Diff with `diff -w` (ignore whitespace)
to see real content changes, port only genuine functional fixes, then re-apply
the four "kept" items above and re-run clang-format. The filter clamp/move in
`yam.c` is the easiest thing to lose — verify it survives.
