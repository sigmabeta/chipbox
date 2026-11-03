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
- **SCSP-only ring-pointer upkeep in `yam.c`** — the modulation ring and its rotation
  index are SCSP-only state (AICA has no `0x600` ring-buffer registers and no
  ring-based modulation read). `generate_samples` keeps the index in a local rather
  than in `state->bufptr`, and `render` seeds/advances the shared pointer only for
  `version == 1`. Output is bit-identical; an upstream sync would reintroduce the
  unconditional per-sample read/write of the shared pointer.
- **SCSP skips the AICA filter-envelope advance in `yam.c`** — `generate_samples`
  gates the low-pass filter-envelope block (`env_adjustrate` / `env_needstep`,
  `flv[]` / `lpflevel` / `lpfstate`) on `version != 1`. The envelope is AICA-only
  observable: PlayStatus exposes `lpfstate` / `lpflevel`, and AICA can set
  `LPOFF=1` while still surfacing them, so the gate is the format, not `lpoff`.
  SCSP never applies the filter, so its per-sample advance was dead work. Output is
  bit-identical; an upstream sync would reintroduce the advance on the SSF path.
- **SSF sorts channels only when modulation links exist in `yam.c`** — the existing
  `version == 1` priority scan sets `sort_needed` when any channel's `mdxsl` or
  `mdysl` is nonzero. With no link, `qsort` and `render_priority_compare` are
  skipped and the render loop uses identity order. Equal-key `qsort` was
  identity-preserving on the measured platforms, but channel order is observable
  through the shared `randseed` (noise sampler / LFO modes), so a resync that
  restores the unconditional sort can change audio on noise-heavy tracks.
- **Portable DSP microprogram predecode in `yam.c`** — `struct DSP_OP dsp_ops[128]`
  plus `dsp_build_ops()` cache each microprogram step (resolved operand indices,
  constants, addresses, flags, and merged empty-instruction runs) as plain data;
  `dsp_invalidate()` now runs unconditionally from every decode-affecting write
  (`coef` / `madrs` / microprogram words, SCSP `0x402` and AICA `0x2804` ring
  base/size, `yam_setram`, enable/clear). No executable memory and no JIT —
  `ENABLE_DYNAREC` / `dynacompile` stay dead. An upstream sync must keep the table
  *and* every invalidation site, or renders go stale.
- **DSF ARM fetch-region retention across local branches in `arm.c`** —
  `renew_fetch_region` records `fetchstart` / `fetchvalid`; `branch_pc_changed()`
  keeps a pointer-mapped region across a same-region `B` / `BL` instead of clearing
  `maxpc`, and never retains a callback-mapped region. Cross-region branches,
  exception entry, block entry, non-branch PC writes (`LDR`/`LDM`/`MUL`/data-proc)
  and `hw_sync` still invalidate exactly as before.
- **Portability adaptations** (needed to build for Android/JVM, non-x86):
  - `yam.c`, `c68k/core.h`: x86 `regparm(3)` / `__fastcall` FASTCALL paths
    disabled.
  - `yam.c`: `(unsigned long)` casts on `&YAMSTATE->dynacode` (pointer→int).
  - `sega.c`: `HAVE_SATSOUND` macro replaced with explicit
    `offset_to_satsound != 0` checks.
- **Formatting** — the tree was formatted once at vendoring time, which is why a
  raw `diff` against upstream looks enormous (~99% reformatting). `Core/` is
  deliberately **not** reformatted now: `Core/.clang-format` sets
  `DisableFormat: true`. New or edited code should match the surrounding style and
  the root limit of 120 columns.

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
the six "kept" items above. Do not re-run clang-format over `Core/`
(`DisableFormat: true`); match the surrounding style and the 120-column limit
instead. The filter clamp/move in `yam.c` is the easiest thing to lose — verify it
survives.
