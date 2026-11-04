# Vendored 2SF core — `Core/vio2sf/`

`Core/vio2sf/` is a vendored copy of **vio2sf** (the DeSmuME-derived Nintendo DS
sound emulator used to play 2SF/MININCSF). Upstream:

- https://bitbucket.org/losnoco/vio2sf.git (`src/vio2sf/` subtree)

The chipbox JNI glue (`2sf.cpp`, `2sf.h`, `kotlin-jni.cpp`, `Twosf_Web.cpp`,
`CMakeLists.txt`) and the `Core/vio2sf/vio2sf.{c,h}` loader are ours — they have
no upstream counterpart in `src/vio2sf/`.

## Sync status — checked 2026-06-01 against upstream `03a0c95` (2025-04-06)

**Functionally in sync.** Diffing `Core/vio2sf/desmume/` against upstream
(ignoring whitespace) shows **no algorithmic differences** — the DSP/resampler
path is identical (both share the same `resampler.c` with none/blep/linear/
cubic/sinc modes). Everything that differs is either a local adaptation or
clang-format noise. There is nothing functional to pull from upstream.

### Local changes (do not let an upstream sync clobber these)

Everything below is chipbox-only. An upstream sync that replaces
`Core/vio2sf/desmume/` or `Core/vio2sf/vio2sf.c` must re-apply all of it, or the
2SF build silently regresses (correctness, memory ownership, and the measured
arm64 wins).

- **Header/struct reorganization**: `SPU.h` → `SPU.hpp`; `channel_struct`,
  `SPU_struct`, `WavWriter` moved out of the header into `SPU.cpp`;
  `SoundInterface_struct` moved from `spu_exports.h` into `state.h`.
- **Portability**: `FORCEINLINE` (`__forceinline` / `always_inline`) stripped
  throughout `SPU.cpp`; the `sputrunc()` overloads replaced with explicit
  `u32floor_float` / `u32floor_double` calls.
- **Dead-code removal**: the foobar2000 push-audio pump
  `SPU_Emulate_user()` (the `SNDCore->GetAudioSpace/UpdateAudio` path) is gone —
  chipbox drives audio through its own JNI pull model.
- **ROM-coverage accounting removed** (`MMU.c`, `NDSSystem.c`, `state.c/.h`):
  the `array_rom_coverage` bitmap and every `bit_array_set` write site are gone
  (no reader existed), and `state_setrom()` no longer takes an
  `enable_coverage_checking` argument. `vio2sf.c` calls the shortened
  signature. The vendored `barray.c`/`barray.h` files are kept (other vendored
  cores use their own copy) but `barray.c` is **not in the 2SF build** — do not
  re-add it to `CMakeLists.txt`.
- **Per-track ownership** (`vio2sf.c`): `xsf_start` releases the previous core
  and loader buffers at entry; `xsf_term` frees the `NDS_state` core, the
  SAVE-section buffer (`state.state`), and the ROM exactly once; both
  `xsf_start` error paths release the calloc'd core; `xsf_gen` returns 0 when
  there is no core; `load_twosf_map` clears its slot before freeing on `realloc`
  failure. The SAVE buffer is freed right after `state_loadstate` restores it.
  These frees are the fix for unbounded native memory across track changes — a
  sync that drops them restores the leak.
- **Resampler fast paths** (`resampler.c`): `resampler_set_rate` memoizes the
  last `double` rate (`last_rate`/`has_last_rate`, copied by
  `resampler_dup_inplace`) and skips the reciprocal recompute on a repeat; the
  ZOH path replaces `fmodf(phase, 1.0f)` with the equivalent branchless
  `phase >= 2^24 ? 0 : phase - (int)phase` form. Both are bit-identical.
- **SPU dispatch specialization** (`SPU.cpp`): `SPU_Mix`/`____SPU_ChanUpdate`'s
  `switch(FORMAT)`/`switch(CHANNELS)` per-sample body is now the
  `FetchChannelSample<FORMAT>` / `MixChannelSample<CHANNELS>` /
  `chan_update<FORMAT, CHANNELS>` template set, dispatched once per channel per
  buffer by `chan_dispatch`; the interpolation mode and mute mask are read once
  per buffer. `isChannelMuted` / `spuInterpolationMode` are `static inline`
  (file-local), not exported. Sample arithmetic is unchanged.
- **Hidden visibility** (`CMakeLists.txt`): the JVM/Android shared library is
  built with `-fvisibility=hidden`, exporting only the `JNIEXPORT` entry points
  (the WASM build keeps its `EMSCRIPTEN_KEEPALIVE` exports). This is required
  for the win above: it lets the compiler inline the core's own small helpers
  instead of emitting interposable PLT calls at the per-sample mixing sites.
- **clang-format** applied to the whole tree (`Core/.clang-format`). This is why
  a raw `diff` looks large; the substantive content matches upstream.

### Upstream-only change we deliberately skipped

- `cp15.c` / `cp15.h`: upstream `03a0c95 "De-inline exported function"` removed
  `INLINE` from `armcp15_isAccessAllowed`. This is a linkage tweak for exporting
  the symbol; chipbox builds the core as a single library, so keeping it inline
  is fine and there is no reason to port the change.

### If you do re-sync in the future

Diff with `diff -w` (ignore whitespace) to find real content changes, port only
genuine functional fixes, then re-apply the local items above and re-run
clang-format. Watch the `SPU.cpp` struct relocation and `FORCEINLINE` removal —
those are the easiest things to lose.
