# slopsf: independent tempo & pitch playback controls — design

Status: **design only, not implemented.**
Target: the slopsf HLE PSX core (`cbox/.../emulators/psf/real/src/main/cpp/slop`).

## Goal

Expose two **independent** playback knobs to consumers (desktop tools,
Android JNI/Kotlin, optionally UI):

- **Tempo** — change song speed while *preserving instrument pitch*.
- **Pitch** — transpose every note while *preserving tempo / event timing*.

Both default to `1.0` (no change) and compose freely (e.g.
`tempo = 0.8, pitch = 1.2`).

## Why two knobs (not one resample)

The PS1/PS2 audio path has two physically distinct clocks:

1. The **sequencer timebase** — the RootCounter (`ioptimer`). Each
   period it crosses raises an IRQ that ticks the music sequencer
   (note-on/off, tempo meta, etc.).
2. The **voice sample rate** — the per-voice SPU pitch register, which
   sets how fast each ADPCM sample is played back.

These are independent on real hardware. Naive output resampling
couples them (the "chipmunk" effect: speed and pitch move together).
Scaling each clock separately gives orthogonal, artifact-free controls.

This is the direct generalization of the RootCounter period-coalescing
fix (chipbox `4e234f60`, `ea5f2158`): that work established that the
sequencer ticks exactly once per RootCounter period and that SPU pitch
is paced separately — exactly the property these knobs exploit.

## Knob 1 — Tempo (pitch preserved)

**Chokepoints:** the two RootCounter advance sites, both calling
`ioptimer_advance(RCNT(), 768)`:

- `slop/hle.c` — `hle_advance()` (the `adv_accum >= 768` loop body)
- `slop/hle.c` — `hle_ps2_pump_tick()` (PSF2 per-sample pump)

**Mechanism:** scale the cycle count fed to the timer by `tempo`,
carrying a fractional remainder so non-integer ratios stay exact with
no long-term drift:

```
scaled              = 768 * tempo + g_hle.tempo_rem
uint32 whole        = (uint32) scaled
g_hle.tempo_rem     = scaled - whole          // carry fraction
ioptimer_advance(RCNT(), whole)
```

- `tempo = 2.0` → periods cross 2× as often → sequencer 2× → music at
  double speed; SPU voice clock untouched ⇒ **pitch unchanged**.
- `tempo = 0.5` → half speed, pitch unchanged.
- Both advance sites must use the **same** scaled value / shared
  remainder so pump and non-pump cadence stay identical (the existing
  code is careful about this; see the `hle_ps2_pump_tick` comment).

**Edge cases / limits:**

- Very high tempo backlogs the softcall FIFO (depth 64) and the
  `rcnt_missed` replay cap (32). Safe within ~0.1×–4×; the public
  setter should clamp.
- `tempo` must never be ≤ 0 (would stall the sequencer / divide
  trouble). Clamp to a positive minimum.

## Knob 2 — Pitch (tempo preserved)

**Chokepoints:** the SPU register write funnels — every voice
register write passes through exactly one of:

- `slop/iop.c` — `iop_spu_sw()` (PS1, `0x1F801C00–0x1F801DFF`)
- `slop/iop.c` — `iop_spu2_sw()` (PS2 SPU2, `0x1F900000–0x1F9007FF`)

**Mechanism:** detect writes to the **per-voice pitch / sample-rate
register** (voice base + `0x04`: PS1 `0x1F801C00 + v*0x10 + 0x04`,
v = 0..23; SPU2 the analogous per-voice offset within each core) and
scale the 16-bit value by `pitch` before passing it to `spu_sh`:

```
if (is_voice_pitch_reg(a)) {
    uint32 p = (uint32)((d & 0xFFFF) * pitch + 0.5f);
    d = (d & ~0xFFFF) | clamp(p, 1, 0x3FFF);   // 0x1000 = 1.0
}
```

- Scales every note's playback rate by a constant ⇒ notes sound
  higher/lower.
- Sequencer timebase untouched ⇒ **tempo / event timing unchanged**.
- Independent of Knob 1.

**Edge cases / limits:**

- Clamp to the SPU's 14-bit pitch range (`1 .. 0x3FFF`). Extreme
  up-shift clips at the hardware max — same behavior as real hardware.
- Pitch-modulated voices (PMON) scale consistently because the base
  rate written here is what gets scaled.
- The funnel sees 16-bit half-word writes (`mask` low/high). Apply the
  scale per half-word matching the pitch register's address, not to
  unrelated registers sharing the 32-bit slot.

## API surface (proposed)

Native (`Psf.h`):

```c
void  set_tempo(float t);   // default 1.0; clamped
void  set_pitch(float p);   // default 1.0; clamped
float get_tempo(void);
float get_pitch(void);
```

- State lives in the slop core (`g_hle`), defaulted to 1.0 at
  `loadFile`/teardown. Reported via `get_diagnostics`.

Desktop (`psf-tools` renderers): `--tempo N` and `--pitch N` flags —
useful for A/B/oracle verification.

Android: two JNI entry points in `kotlin-jni.cpp` → Kotlin player API
→ (optionally, later) UI sliders. Wiring is incremental and can land
after the engine + desktop layer is verified.

## Verification plan

Using the psf-tools A/B/C harness:

- **Tempo invariant:** `--tempo 2.0` ⇒ song reaches end-of-sequence in
  ~half the rendered samples vs `1.0`, with the **same pitch
  spectrum** (per-note SPU pitch register values unchanged; compare
  KON pitch distribution).
- **Pitch invariant:** `--pitch 2.0` ⇒ identical sequencer event
  timeline (KON timestamps unchanged) with voice pitch registers
  uniformly ×2 (clamped).
- **Identity:** `tempo = pitch = 1.0` ⇒ byte-identical to current
  output, including the VP regression anchor
  `32528061a0c5` (VP "120 Artifact.psf" 5s).
- **Composition:** `tempo = 0.5, pitch = 2.0` ⇒ half-speed timeline +
  ×2 voice pitch, independently.

## Open questions

1. Public clamp ranges. Suggested: tempo `0.1 .. 4.0`,
   pitch `0.25 .. 4.0`.
2. Live-adjustable mid-playback vs apply-on-load only. Live requires
   the setters to be lock-free w.r.t. the audio render thread (the
   scalars are single words; a relaxed atomic / volatile write is
   sufficient — no structural reallocation).
3. Whether to also offer a coupled "resample" mode (classic
   speed+pitch together) for completeness, or keep strictly the two
   orthogonal knobs.

## Out of scope

- Time-stretch / pitch-shift DSP (phase vocoder etc.). Not needed: the
  HLE sequencer + SPU give us native, artifact-free separation.
- The unrelated `r3000` fetch SIGSEGV on Misadventures / Persona 2
  Innocent Sin (unhandled HLE BIOS syscall) — tracked separately.
