# Loudness / volume system — follow-up improvements

Status: **design only, not implemented.**
Owns three independent follow-ups to the BS.1770 / EBU R 128 loudness work
landed alongside this doc:

1. **Float-domain processing in `VolumeProcessor`** — eliminate quantization
   noise on boosted-quiet tracks, which is the dominant audible artifact of
   the new -14 LUFS normalization target.
2. **Surface the measurement in `VolumeDebugInfo`** — let the debug
   PlaybackStatus screen show the LUFS / dBTP figures that drove the gain,
   not just the resulting multiplier.
3. **Measure loudness range (LRA)** — store the per-track dynamic-range
   spread alongside LUFS / true peak, as a free byproduct of the
   measurement we're already doing.

The three are independent: each can land in any order without touching the
other two. They share this doc only because they're all small follow-ups to
the same recently-landed system.

## Context

The loudness work replaced peak-sample normalization with BS.1770 integrated
loudness + true peak. The wiring is:

```
EmulatorPcmSource ─┐
                   │  loudnessLufs / truePeakDbtp
CachingPcmSource ──┼─► PcmTrackSource ──► Generator ──► AudioBuffer
                   │                                      │
CachedFilePcmSource┘                                      ▼
                                                        Speaker
                                                          │  setNormalization(lufs, dbtp)
                                                          ▼
                                                       VolumeProcessor
                                                          │  modifications["normalization"] = gain
                                                          ▼
                                                       process(): integer Short × Double rounding
```

`normalizationGain(lufs, dbtp)` returns `min(10^((target − lufs)/20),
10^((peakCeiling − dbtp)/20))`, capped at `MAX_GAIN = 5.0`. Target is
-14 LUFS, peak ceiling is -1 dBTP — both match streaming-service conventions.

## Improvement #1 — Float-domain sample math in `VolumeProcessor`

### Problem

Most chiptunes sit around -20 to -25 LUFS unmastered. To reach the -14 LUFS
target, the system *boosts* them, often by 2–4× linear. `VolumeProcessor.process`
applies the gain via:

```kotlin
private fun scaleSample(sample: Short, gain: Double): Short =
    (sample * gain)
        .roundToInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
```

For boosted-quiet content this rounds-to-int every sample with the gain
already applied, then quantizes back to 16-bit. The round-then-quantize
introduces stair-step distortion concentrated at the noise floor — audible
on tracks like SNES sample-based music with a quiet pad, where the boost
amplifies what was previously inaudible quantization into a perceptible
hiss / "graininess."

Float-domain processing keeps full precision throughout the modification
chain (potentially across many modifications — ducking × master × normalization
× future EQ × …) and quantizes exactly once at the output.

### Chokepoints

- `VolumeProcessor.process(audioInput, ...)` (`cbox/common/player/common/api/.../VolumeProcessor.kt:144`)
  — the per-frame loop multiplying samples by `actualGain * fadeGain`.
- `scaleSample(sample: Short, gain: Double): Short` (same file, ~line 207).

### Mechanism

Two viable shapes:

**Option A — convert in place per buffer.** Allocate a transient
`FloatArray` (or `DoubleArray`) the same length as `audioInput.size` on each
`process` call, copy samples in, apply gain in floats, quantize back. Simple
to write; allocates one scratch buffer per call. The buffer manager already
recycles `ShortArray`s but not float scratch — this would create GC pressure
proportional to playback rate (one scratch alloc per ~93 ms buffer at the
existing pool size).

**Option B — stream sample-by-sample in floats.** Keep the loop sample-shaped,
but compute the gain math in `Double` and only call `.toShort()` (with
clamp + round) at the write back to `audioInput`. Zero allocations; same
clamping semantics as today; the only difference from today is that the
intermediate product (`sample * gain * fadeGain * …`) stays in `Double`
right up to the final write. Effectively this is what the current code
already does for the single gain — the fix is to keep it in float when
ducking + master + normalization all stack.

**Option B is preferred** — it's smaller, allocation-free, and removes the
one-quantize-per-modification implicit in the current chain. (The current
chain *combines* modifications into `combinedGain` before applying, so
there's actually only one quantize per sample today — but if anything
in the future wants to apply a per-sample modification rather than a
constant multiplier, B sets it up for that without further changes.)

### Sketch

```kotlin
for (sampleIndex in audioInput.indices step SHORTS_PER_FRAME) {
    val fadeGain = ...                            // unchanged
    val gain = actualGain * fadeGain
    audioInput[sampleIndex]     = quantize(audioInput[sampleIndex].toDouble() * gain)
    audioInput[sampleIndex + 1] = quantize(audioInput[sampleIndex + 1].toDouble() * gain)
    actualGain = approach(actualGain, targetGain)
}

private fun quantize(value: Double): Short =
    value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
```

Functionally indistinguishable from today's `scaleSample`; the *real* win
comes when this loop later grows additional per-sample stages (EQ,
limiter, …) since each can mutate `value` in `Double` before the final
`quantize`.

### Edge cases / limits

- `Short.MIN_VALUE.toInt() * gain` overflow is already guarded by the
  `.toInt().coerceIn(...)` — no change there.
- The change is *bit-exact* to today for any single gain stage (same
  `roundToInt` + clamp). Regression risk is minimal as long as the order
  of operations within the existing single multiply isn't reordered.
- The asymmetric `approach()` ramp is unchanged — `actualGain` is still
  stepped per frame in `Double`.

### Verification

- Existing playback should sound *identical* — no perceptible difference
  on a track that doesn't need boost. The point of this change is to
  preserve quality once boost gains stack up (≥2× linear).
- A/B test: pick a quiet SNES-sample track measuring around -24 LUFS.
  Pre-change: hear quantization grain in pad-only sections. Post-change:
  clean.
- The `LoudnessLog` line for the same track should be byte-identical
  (the measurement is upstream of `VolumeProcessor`).

### Out of scope

- Moving the storage / wire format off `Short`. Sticking with 16-bit PCM
  end-to-end; only the in-process math goes float.
- Per-sample dithering before quantization. Worth considering only after
  Improvement #1 is in place and we have a concrete pad-noise case to
  fix; without dither, integer truncation is already adequate for chiptune
  source material.

---

## Improvement #2 — Surface LUFS / dBTP in `VolumeDebugInfo`

### Problem

The debug PlaybackStatus screen currently sees normalization as one entry
in `modifications: Map<String, Double>`:

```
modifications = { "normalization" → 1.82, "duck" → 1.0, "master" → 1.0 }
```

You can't tell whether `1.82` came from a -20.4 LUFS track being boosted
or a -8 LUFS track being limited by the true-peak ceiling. You also can't
see whether the true-peak ceiling clamp is the active term — i.e. whether
loudness alone wanted to push the gain higher but the peak cap held it
down. Without that you're flying blind on whether the system is doing
what you think.

### Chokepoints

- `VolumeDebugInfo` data class (`cbox/common/player/common/api/.../VolumeDebugInfo.kt`)
  — the snapshot type.
- `VolumeProcessor.debugSnapshot()` — builds the snapshot.
- `Speaker.runConsumeLoop()` (`cbox/common/player/speaker/api/.../Speaker.kt:201` area)
  — has the live `(loudnessLufs, truePeakDbtp)` per buffer and currently
  feeds only the resulting gain into the processor.

### Mechanism

Add two fields to `VolumeDebugInfo`:

```kotlin
data class VolumeDebugInfo(
    val targetGain: Double,
    val actualGain: Double,
    val maxGain: Double,
    val modifications: Map<String, Double>,
    val loudnessLufs: Double = Double.NaN,             // new
    val truePeakDbtp: Double = Double.NEGATIVE_INFINITY, // new
)
```

`Speaker` already has these per-buffer (`audioBuffer.loudnessLufs`,
`audioBuffer.truePeakDbtp`). The cleanest plumbing is to stash them in a
private field on `VolumeProcessor` next to `modifications`, set via the
existing `setNormalization` call site, and read back in `debugSnapshot`:

```kotlin
class VolumeProcessor {
    @Volatile private var lastNormalizationLufs: Double = Double.NaN
    @Volatile private var lastNormalizationTruePeakDbtp: Double = Double.NEGATIVE_INFINITY

    fun setNormalization(loudnessLufs: Double, truePeakDbtp: Double) {
        lastNormalizationLufs = loudnessLufs
        lastNormalizationTruePeakDbtp = truePeakDbtp
        setModification(KEY_NORMALIZATION, normalizationGain(loudnessLufs, truePeakDbtp))
    }

    fun debugSnapshot(): VolumeDebugInfo = VolumeDebugInfo(
        // ... existing fields ...
        loudnessLufs = lastNormalizationLufs,
        truePeakDbtp = lastNormalizationTruePeakDbtp,
    )
}
```

`PlaybackStatus`'s VolumeProcessor section then adds a row formatted as
`"-20.4 LUFS, -1.8 dBTP → 1.82×"`.

### Edge cases / limits

- `Double.NaN` ⇒ display as `"—"` or `"unmeasured"`.
- `Double.NEGATIVE_INFINITY` true peak ⇒ display as `"—"` (no audible
  peak).
- Concurrency: `setNormalization` is called from the speaker coroutine;
  `debugSnapshot` is called from the UI / status sampling. Two `@Volatile`
  doubles match the existing `ConcurrentHashMap` choice in the same class.

### Verification

- Open the debug PlaybackStatus screen during playback of a fresh render.
  Watch LUFS go from `—` → first reading → settled value over the first
  400 ms.
- On a known-loud track (≥ -10 LUFS, e.g. a brick-walled remix), watch
  the true-peak field show > -1 dBTP and the resulting gain hit the
  peak-cap branch (`gain` should equal `10^((-1 - dbtp)/20)`, not the
  loudness branch).
- On a cache hit, the values should appear immediately at the same
  reading the previous render produced (verifies the cache header
  round-trip).

### Out of scope

- Live LUFS / momentary loudness display (3 s window). Useful but
  requires plumbing a second measurement state — file under future work.
- Per-buffer time-series graph of LUFS. Same caveat.

---

## Improvement #3 — Loudness Range (LRA)

### Problem

Integrated LUFS is the per-track *average*; it tells you nothing about
how dynamic the track is. A track that's all quiet pad until a single
loud crescendo can measure the same LUFS as one that sits steadily at
the same volume throughout. BS.1770 also defines a Loudness Range
metric — the spread between the 10th and 95th percentile of short-term
(3 s) loudness measurements, in LU. EBU Tech 3342 gives the full
algorithm.

Why care:

- **Telemetry / UI:** display LRA on the now-playing screen alongside
  the existing position / length. "Dynamic" vs "compressed" is
  user-meaningful info for a chiptune library.
- **Future smarts:** drives possible automatic detection of "static
  tone" tracks (LRA ≈ 0 LU), or could feed a future compressor's
  threshold/ratio settings.
- **Free:** the short-term loudness windows needed for LRA are mostly
  already in `EbuR128`'s sub-block ring — adding a parallel
  short-term-block accumulator is a small extension to the existing
  measurer.

### Chokepoints

- `EbuR128` (`cbox/common/player/common/api/.../EbuR128.kt`).
- `PcmCacheFile.Header` + `writeHeader` / `readHeader` — needs to carry
  one more `Double`.
- `PcmTrackSource` interface — new `loudnessRangeLu: Double` getter.
- `AudioBuffer` — new `loudnessRangeLu: Double` field (only if the UI
  wants live LRA before the cache header is finalized; otherwise reading
  it once at track-end via `Speaker` is sufficient).

### Mechanism

EBU Tech 3342 LRA algorithm:

1. Compute **short-term loudness** in 3 s windows with 66 % overlap
   (i.e. emit a new value every 1 s).
2. Apply absolute gate: discard any window with short-term loudness
   below -70 LUFS.
3. Apply relative gate: discard any window more than 20 LU below the
   integrated loudness of the program *of the gated set*.
4. Of the survivors, find the 10th and 95th percentiles (sort, pick by
   index).
5. `LRA = pct95 - pct10` (in LU).

In our existing `EbuR128`:

- The 100 ms sub-block infrastructure (`subBlockFrames` + `subBlockSums`
  ring) already exists. A 3 s short-term window is 30 sub-blocks.
  Maintain a second ring of 30 sub-block partial sums, sliding by 10
  sub-blocks (1 s) per emission, parallel to the existing 4-sub-block
  /400 ms /100 ms-emission ring for integrated.
- Each emitted short-term mean-square gets stored in a second
  `ArrayList<Double>` (`shortTermMeanSquares`).
- A new public `loudnessRangeLu(): Double` performs the EBU 3342 gating
  + percentile selection at finalize. Returns `Double.NaN` for tracks
  with < 1 valid short-term window (very short tracks).

Memory shape: 30 doubles in the second ring + 1 short-term entry per
second of audio in the ArrayList. For a 30-minute track that's 1800
doubles = 14 KB on top of the existing integrated state. Trivial.

### Storage

Cache header: add one `Double` after `truePeakDbtp` (header reserved
region still has ≥ 32 free bytes after the LUFS / true-peak work).

`PcmTrackSource.loudnessRangeLu: Double get() = Double.NaN` default in
the interface; `CachingPcmSource` / `EmulatorPcmSource` expose live;
`CachedFilePcmSource` replays from header.

`AudioBuffer` carries `loudnessRangeLu: Double = Double.NaN` — only
needed if a live UI wants to display LRA before the writer completes.
Otherwise leave it off the buffer and read directly from
`PcmTrackSource` once when the track loads.

`LoudnessLog` extends its line:
```
Track Foo: -20.4 LUFS, -1.8 dBTP, 8.3 LRA. Multiply by 1.82x to reach -14 LUFS.
```

### Edge cases / limits

- Tracks shorter than ~4 s have < 1 complete 3 s window. LRA is
  undefined; return NaN. UI should display as `"—"`.
- Tracks with very narrow dynamic range (e.g. a sustained sine sweep
  or a static tone) will have LRA approaching 0 LU. That's correct, not
  a bug.
- The 10th/95th percentile picks are *off the sorted list of gated
  blocks*. Standard practice is `pct = sorted[round((p/100) * (n−1))]`;
  no interpolation needed for our purposes.
- Relative gate threshold is **20 LU** below integrated (not 10 LU like
  the integrated stage-two gate). Easy to get wrong; matches EBU Tech
  3342 exactly.

### Verification

- LRA of a brick-walled / heavily-compressed track ⇒ small (≤ 5 LU).
- LRA of a track with very quiet intro + loud body ⇒ large (≥ 15 LU).
- LRA of a sustained chord with no dynamics ⇒ approaches 0 LU.
- Cross-check with `ffmpeg -af ebur128=peak=true` (which reports LRA)
  on the same source — values should match within ~0.2 LU.
- Cache round-trip: render once with logs, render again from cache,
  the LRA value in both `LoudnessLog` lines should be byte-identical.

### Out of scope

- Using LRA to feed a compressor/limiter ratio. Telemetry only for
  this slice — actually shaping the signal off the LRA value is a
  separate, much larger piece of work that would need a real-time
  dynamics processor in `VolumeProcessor`.
- Momentary (400 ms) loudness reporting. We already compute the
  underlying 400 ms blocks for integrated; exposing them as a live
  value is a small additional step but unrelated to LRA.
- Displaying LRA in the playback UI. The plumbing through the cache
  header + `PcmTrackSource` lands the value; *where* it surfaces in
  the UI is a UI question, not a player-stack question.

---

## Dependencies / ordering

None of the three depend on the others. Suggested order (smallest blast
radius first):

1. **#2 (debug surface)** — smallest, lets the next two be validated
   from the running app rather than from logs alone.
2. **#1 (float math)** — bit-exact behavioral change for the existing
   single-gain path; the real value pays out only once additional
   per-sample stages get added on top of it. Safe to land any time.
3. **#3 (LRA)** — biggest of the three because it touches the cache
   header, `PcmTrackSource`, and (optionally) `AudioBuffer`. The
   header change is additive (one more `Double` in reserved space) so
   the same "don't bump VERSION" stance from the LUFS/true-peak work
   applies — stale `.pcm` files read back `0.0 LRA`, which `LoudnessLog`
   and any UI should treat as `"—"`.
