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

- **Header/struct reorganization**: `SPU.h` → `SPU.hpp`; `channel_struct`,
  `SPU_struct`, `WavWriter` moved out of the header into `SPU.cpp`;
  `SoundInterface_struct` moved from `spu_exports.h` into `state.h`.
- **Portability**: `FORCEINLINE` (`__forceinline` / `always_inline`) stripped
  throughout `SPU.cpp`; the `sputrunc()` overloads replaced with explicit
  `u32floor_float` / `u32floor_double` calls.
- **Dead-code removal**: the foobar2000 push-audio pump
  `SPU_Emulate_user()` (the `SNDCore->GetAudioSpace/UpdateAudio` path) is gone —
  chipbox drives audio through its own JNI pull model.
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
