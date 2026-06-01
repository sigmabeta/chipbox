# Vendored USF core — lazyusf2

This tree is a vendored copy of the **lazyusf2** Nintendo 64 RSP/RCP emulator
(the `r4300/`, `rsp_hle/`, `rsp_lle/`, `ai/`, `pi/`, `rdp/`, `ri/`, `si/`,
`vi/`, `main/`, `memory/`, `usf/` directories). Upstream:

- https://github.com/kode54/lazyusf2

The chipbox JNI glue around it (`Usf.cpp`, `Usf.h`, `kotlin-jni.cpp`,
`Usf_Web.cpp`, `CMakeLists.txt`, `Emscripten.Makefile`) is ours and has no
upstream counterpart.

## Sync status — synced 2026-06-01 to upstream `f771b33` (2026-03-09)

The vendored core originated as **Juergen Wothke's 2018 lazyusf fork**
(`_wothke/n64plug.cpp`, *"Based on kode54's lazyusf2, patched to not rely on
foobar2000"*), branched from `kode54/lazyusf2` at roughly `311b9a3~1` (just
before the 2022-02 *"Update lazyusf2 from Cog and mupen64plus"*). On 2026-06-01
it was synced forward to upstream HEAD `f771b33` via a phased, corpus-validated
3-way merge (see history below), **keeping the Wothke interrupt model**.

### Build surface

`CMakeLists.txt` builds the **cached-interpreter + RSP-HLE** configuration
(`r4300/empty_dynarec.c`; no `r4300/x86`, `r4300/x86_64`, or `new_dynarec`).
Every recompiler directory is **dead code in the shipped binary** and was left
untouched by the sync — it can never be the cause of a runtime difference. The
merge surface was the `usf_SRCS` list (Android `.so`, JVM, and WASM share it).

### Local changes kept (do not let an upstream sync clobber these)

The deliberate divergence is the **interrupt model**: this fork uses the older
mupen64plus model and **lacks kode54's `cycle_count` interrupt-accuracy rework**.
This was preserved (see "deliberately skipped" below). The files that differ
from upstream HEAD because of it:

- **`usf/usf_internal.h`** — no `int cycle_count;` field in `usf_state_t`
  (upstream HEAD has it; the Wothke fork removed it).
- **`r4300/interupt.c`** — the Wothke `SPECIAL_INT` / `SPECIAL_done` scheduling
  and the *"hack-fix for freezing in Perfect Dark"*. (Cog's only change to this
  region was trailing whitespace; the merge kept ours.)
- **`r4300/exception.c`, `r4300/r4300.c`, `r4300/cached_interp.c`** — Wothke's
  no-`cycle_count` bodies, with Cog's harmless `#ifdef DYNAREC` dead-code guards
  and the `asm volatile`→`__asm __volatile` portability rename merged in.
- **`main/savestates.c`** — Wothke savestate shape (no `cycle_count`).
- **`ai/ai_controller.c`** — Wothke's early `CP0_COUNT_REG` return and the
  `& ~7` mask on `remaining_dma_duration` (kept on top of Cog's `dcb966f`
  register-mirroring change).
- **`r4300/cp0.c`, `r4300/mi_controller.c`, `r4300/pure_interp.c`** — local-only
  changes; upstream did not touch these in `311b9a3..f771b33`, so they were left
  exactly as the fork had them.

### Local additions to the build

- **`rsp_hle/hvqm.c` + `rsp_hle/re2.c`** were vendored and added to `usf_SRCS`
  (and the `Emscripten.Makefile` OBJS). Cog's `rsp_hle/hle.c` references
  `hvqm2_decode_sp1_task` / `hvqm2_decode_sp2_task` / `decode_video_frame_task`
  unconditionally in its ucode dispatch table, so without these two files the
  library fails to load (undefined symbols). The original sync plan proposed
  skipping them "until a track needs them" — that was wrong; they are mandatory
  at this revision.

### Upstream-only changes we deliberately skipped

- **The kode54 `cycle_count` interrupt-accuracy model** (the "Option B" rework).
  The A/B corpus (below) showed the Wothke model leaving no track measurably
  wrong, so adopting `cycle_count` — which would also mean dropping the Perfect
  Dark hack-fix — was not worth the regression risk.
- **The dead-code recompilers** (`r4300/x86`, `r4300/x86_64`, `new_dynarec`).
  Not compiled in this configuration, so not synced.

## Corpus validation (A/B, 244-track USF corpus)

Each phase was rendered over a 244-game USF corpus and diffed against the prior
build with `apps/abrender` (12-way parallel; see its README). Cumulative result,
pre-sync vs fully-synced:

- **215 bit-identical**, **23 changed**, **6 pre-existing failures unchanged**,
  **0 regressions** (no track went silent or newly errored).
- ~15 of the 23 are below ~−50 dBFS diff-RMS (numerically negligible/inaudible);
  ~8 are subtly audible (`ΔRMS ≈ 0`, waveform differences) on big-name titles —
  Zelda OoT & Majora's Mask, Super Mario 64, Star Fox 64, Mario Kart 64, Doshin.
  All of the audible ones were bisected to a single upstream commit — see
  "What the audible changes are" below.
- The 6 failures are pre-existing and unrelated to the sync: 5 tracks
  crash/hang in an uninterruptible native call, 1 is an invalid rip. They fail
  identically before and after.
- **Conker's Bad Fur Day** renders **silent** both before and after. Cog's
  *"fixes a major crash with Conker"* is moot here (the Wothke fork never
  crashed on it — it rendered silent, and still does); the silence is a separate,
  pre-existing issue.

## What the audible changes are (bisected & verified)

**USF audio is rendered by the low-level RSP interpreter (LLE), not HLE.**
`Usf.cpp` calls `usf_set_hle_audio(g_state, 0)`, so in `rsp_lle/rsp.c`'s
`real_run_rsp`, audio tasks (`OSTask.type == M_AUDTASK`) fall through to
`run_task()` (the cycle-accurate-ish LLE in `rsp_lle/execute.h`). Only graphics
DLists use the HLE engine. **Consequence:** the Cog RSP-HLE alist rewrite
(`alist*.c`, `audio.c`, `musyx.c`, …) does **not** affect USF audio output here —
it is on a path chipbox doesn't take for audio.

Every audibly-changed track was bisected (revert one file, rebuild, compare PCM
hashes to the pre-sync render). All of them trace to a single commit,
**`421f00b` "Fix playback for several USFs"**, via two changes the Wothke fork
had disabled and this sync restores:

1. **`rsp_lle/execute.h`** — re-enables two `check_interupt(state)` calls inside
   the LLE RSP execution loop (the fork had them commented out). Reverting just
   this restores **Zelda OoT v1.0 & v1.2, Majora's Mask, and Star Fox 64**
   bit-for-bit to the pre-sync output.
2. **`main/main.c`** — sets the `g_delay_si/ai/pi/dp` timing flags when a USF's
   `_enablecompare` **and** `_enablefifofull` tags are both set (chipbox reads
   these tags in `Usf.cpp`). Reverting just this restores **Doshin the Giant and
   Mario Artist**. **Mario Kart 64 and Super Mario 64** need *both* reverts.

So the audible differences are a deliberate upstream playback/interrupt-timing
fix restoring behaviour the fork had removed — not a regression. (This is the
strongest claim the corpus supports; it is *not* a measurement against real N64
hardware. The in-tree LLE can't serve as an independent oracle — forcing it for
all tasks deadlocks, since it's wired only as a fallback for unknown ucodes.)

**Per-architecture note:** `ac47dbe`'s VNE fix in `rsp_lle/vu/vne.h` is inside
`#ifdef ARCH_MIN_ARM_NEON`, so it only affects **Android/ARM** builds (it is
inert on the x86-64 JVM host used for this A/B). The corpus numbers above are
from the x86-64 render; ARM playback additionally gets the corrected VNE
(vector not-equal) opcode.

## If you re-sync in the future

Do **not** copy the tree over wholesale. Add the upstream repo as a remote,
fetch it, and `git apply --3way` upstream commits onto this tree in phases,
A/B-testing the corpus between phases. The one merge conflict to expect is the
`interupt.c` `SPECIAL_INT` block (resolve "ours"); everything else 3-way-merges
clean. Preserve the interrupt-model divergence (Option A) above unless the corpus
proves a specific track needs `cycle_count`, and keep `hvqm.c`/`re2.c` in the
build.
