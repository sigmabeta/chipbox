# USF backend ↔ lazyusf2 sync plan

Status: **plan only — no code changes yet.** Reviewed target:
`kode54/lazyusf2` HEAD `f771b33` (2026-03-09).

## 1. Provenance & current state

The vendored core at `cbox/native/usf/` is **Juergen Wothke's 2018 lazyusf
fork** (`_wothke/n64plug.cpp`, © 2018 Wothke — *"Based on kode54's lazyusf2,
patched to not rely on foobar2000"*, + Emscripten glue). It is **not** a stale
checkout of `kode54/lazyusf2`; it shares a common ancestor with kode54's tree at
roughly **`311b9a3~1`** (just before the 2022-02-12 *"Update lazyusf2 from Cog
and mupen64plus"*). Verified: `r4300/recomp.c` and `rsp_hle/hle.c` match
`311b9a3~1` byte-for-byte.

Deliberate local divergence: the Wothke fork uses the **older mupen64plus
interrupt model** (`SPECIAL_INT`/`SPECIAL_done`, a "Perfect Dark freeze"
hack-fix) and **lacks the `cycle_count` interrupt-accuracy rework** that even the
2022 base already has. This touches `interupt.c`, `r4300.c`, `exception.c`,
`cp0.c`, `usf_internal.h`.

### What actually ships (the only files that matter)

`CMakeLists.txt` builds the **cached-interpreter + RSP-HLE** configuration
(`empty_dynarec.c`; **no** `r4300/x86`, `r4300/x86_64`, or `new_dynarec`). So all
recompiler divergence — `gr4300.c`, `assemble.h`, every `r4300/x86*` file — is
**dead code in the shipped binary** and is out of scope for runtime correctness.
The merge surface is the `usf_SRCS` list only (Android `.so`, JVM, and WASM all
use this same source set).

## 2. Upstream changes since the ancestor, grouped by coherent unit

| Unit | Commits | Compiled files touched | Value |
|------|---------|------------------------|-------|
| **Cog/mupen64plus accuracy update** | `311b9a3` | recomp.c (FF), hle.c (FF), cached_interp.c (M), interupt.c (M), exception.c (M), r4300.c (M), savestates.c (M), + many rsp_hle FF | Large accuracy update; **entangled with the interrupt model** |
| **USF playback fix** | `421f00b` | rsp_lle/rsp.c (FF), plugin.c (FF) | *"Fix playback for several USFs"* — directly relevant |
| **RSP HLE improvements** | `ea74bbb`, `4dcf128`, `374bf54` | alist*.c (FF), plugin.c (FF), hle*.h (FF) | Overload import; unsupported-ucode → LLE fallback |
| **Register mirroring** | `dcb966f` | ai_controller.c (M), rdram.c (M), pi_controller.c (FF), rdp_core.c (FF), rsp_core.c (FF), si_controller.c (FF), vi_controller.c (FF) + headers | Hardware-accuracy; mostly FF |
| **DMA bounds checking** | `f771b33` | pi_controller.c (FF) | **Safety/robustness**; clean FF |
| **Warning/whitespace/rename** | `7623661`, `ec1b54a`, `40b328a`, `4e0d1c8`, `13bf3a8` | scattered FF + dead-code dynarec | Cosmetic |

`FF` = vendored matches ancestor, take upstream wholesale. `M` = 3-way merge
(local mods + upstream changes coexist).

## 3. The merge surface (compiled files only)

### 3a. Fast-forward set — take upstream HEAD verbatim (~25 compiled .c + headers)
`main/main.c`, `memory/memory.c`, `pi/pi_controller.c`, `pi/pi_controller.h`,
`r4300/recomp.c`, `rdp/rdp_core.c`, `rdp/rdp_core.h`, `ri/rdram_detection_hack.c`,
`ri/ri_controller.h`, `rsp/rsp_core.c`, `rsp/rsp_core.h`, `rsp_hle/alist.c`,
`alist.h`, `alist_audio.c`, `alist_naudio.c`, `alist_nead.c`, `audio.c`,
`audio.h`, `hle.c`, `hle.h`, `hle_external.h`, `hle_internal.h`, `jpeg.c`,
`memory.c`, `memory.h`, `mp3.c`, `musyx.c`, `plugin.c`, `arithmetics.h`,
`ucodes.h`, `rsp_lle/rsp.c`, `rsp.h`, `execute.h`, `su.h`, `rsp_lle/vu/*.h`
(incl. the `vne.h` "ARM VNE fix" and `vsaw.h`/`vxor.h`/`clamp.h`),
`si/game_controller.c`, `si/si_controller.c`, `si/si_controller.h`,
`usf/usf.c`, `usf/barray.c`, `usf/barray.h`, `usf/resampler.c`, `usf/resampler.h`,
`vi/vi_controller.c`, `vi/vi_controller.h`.

Risk: low individually. These are byte-identical to the ancestor in the vendored
tree, so taking upstream introduces only upstream's own deltas. **Caveat:** some
FF files are members of a cross-file unit (register mirroring, Cog update) and
must land **together with** their `M` siblings, not piecemeal — see §4.

### 3b. Three-way merge set — local mods + upstream changes (~8 files)
| File | local Δ vs base | upstream Δ | upstream unit | notes |
|------|----------------:|-----------:|---------------|-------|
| `r4300/interupt.c` | 92 | 311b9a3 (+6) | Cog | **interrupt-model conflict — the crux** |
| `r4300/exception.c` | 2 | 311b9a3 | Cog | `cycle_count` reset lines |
| `r4300/r4300.c` | 2 | 311b9a3 | Cog | `cycle_count` init |
| `usf/usf_internal.h` | 2 | (+2) | — | `cycle_count` struct field |
| `r4300/cached_interp.c` | 16 | 311b9a3 | Cog | |
| `r4300/savestates.c`→`main/savestates.c` | 2 | 311b9a3 + 7623661 | Cog | savestate format may shift |
| `ai/ai_controller.c` | 5 | dcb966f | register mirroring | |
| `ri/rdram.c` | 4 | dcb966f | register mirroring | |
| `r4300/interpreter_tlb.def` | 2 | 7623661 | warnings | trivial |

### 3c. Local-only set — upstream unchanged, **keep vendored as-is, do not touch**
`r4300/cp0.c`, `r4300/mi_controller.c`, `r4300/pure_interp.c`,
`r4300/interpreter_cop0.def`, `r4300/interpreter_special.def`, and all dead-code
`r4300/x86*` files.

### 3d. Missing files
`rsp_hle/hvqm.c`, `rsp_hle/re2.c` — upstream microcode handlers (HVQM video, an
RE2 ucode), **not in `usf_SRCS`** today. With `4dcf128` (unsupported ucode → LLE
fallback) they are optional for audio. **Decision: skip initially**; only add
(file + `CMakeLists.txt` entry) if a corpus track regresses to silence and traces
to a missing ucode handler.

## 4. The interrupt-model decision (central risk)

Two coherent options — **do not half-merge**:

- **Option A — keep the Wothke interrupt model (recommended starting point).**
  When fast-forwarding `interupt.c`/`exception.c`/`r4300.c` would pull in
  `cycle_count`, instead **port only the non-interrupt parts** of the Cog update
  to these files and retain the local `SPECIAL_INT` model + Perfect Dark hack.
  Lower regression risk; preserves today's known-good playback behavior.
- **Option B — adopt kode54's `cycle_count` model.** Take upstream `interupt.c`
  et al. wholesale, add the `cycle_count` field to `usf_internal.h`, drop the
  Perfect Dark hack. Higher accuracy potential, higher regression risk — **only
  pursue if the A/B corpus shows Option A leaving specific tracks wrong.**

This is the one decision that should be driven empirically by the corpus, not
guessed.

## 5. Phased execution — mirrors the PSF sync (atomic, corpus-validated)

Like `psf-hle-emulator-fixes`, land **small commits, each naming what it
fixes/changes**, A/B-tested against the USF corpus between phases. Work on a
branch off `beta`.

- **Phase 0 — harness.** Confirm the A/B setup: render N corpus tracks to PCM
  with the current build (baseline), keep hashes/waveforms. (User drives
  device/audio listening; I can wire a JVM-target render-to-WAV A/B if useful.)
- **Phase 1 — pure cosmetics (zero behavior risk).** FF the warning/rename/
  whitespace-only files. Build, confirm corpus renders **bit-identical** to
  baseline. Commit: `usf: sync warning/rename-only files from lazyusf2`.
- **Phase 2 — DMA bounds checking.** FF `pi/pi_controller.c` minus the register-
  mirroring hunk (or fold into Phase 4). Safety-only; expect bit-identical.
  Commit: `usf: add DMA bounds checking (lazyusf2 f771b33)`.
- **Phase 3 — USF playback fix + RSP HLE.** FF `rsp_lle/rsp.c`, `plugin.c`,
  `alist*.c`, `hle*.h`, `ucodes.h`, `audio.*`, `jpeg.c`, `mp3.c`, `musyx.c`,
  `memory.*`. A/B the corpus; **expect changes** on some tracks — verify they are
  fixes (compare against a reference player on the same rips), not regressions.
  Commit per coherent fix, naming affected tracks (PSF-style).
- **Phase 4 — register mirroring (coherent unit).** Apply `dcb966f` across all
  its files together (FF siblings + merge `ai_controller.c`, `rdram.c`). A/B.
- **Phase 5 — Cog accuracy update, interrupt model = Option A.** FF `recomp.c`,
  `hle.c` (byte-clean), then merge `cached_interp.c`/`savestates.c` and the
  *non-interrupt* parts of the Cog update. Heaviest A/B pass. Hold the interrupt
  files unless §4 says otherwise.
- **Phase 6 — (conditional) interrupt model Option B.** Only if the corpus shows
  residual wrongness traceable to interrupt timing.
- **Finalize.** Replace this file with a `VENDORING.md` recording the new base
  commit, the retained local adaptations, and any deliberately-skipped upstream
  changes (same format as `cbox/native/ssf` and `cbox/native/2sf`).

## 6. Build / verify per phase

```sh
./gradlew :apps:android:assembleDebug          # ARM .so compiles
./gradlew :apps:jvm:run --args="gui"           # desktop sanity / A/B render host
# (WASM via Emscripten if the JS app is in scope)
```
No ktlint/detekt impact (C/C++ only). **On-device playback verification is the
user's** — each phase hands off a build + the list of corpus tracks expected to
change, with the reason.

## 7. Rollback

Each phase is one (or a few) atomic commit(s) on a branch; `git revert` or branch
reset backs out any phase without disturbing the others. The dead-code dynarec
dirs are never touched, so the recompiler can't be the cause of any regression.
```
