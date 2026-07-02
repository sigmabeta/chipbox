# `:cbox:common:player:buffer:api`

> The producer/consumer hand-off between the generator and the speaker — types only.

The `:api` module for the playback pipeline's **Buffer** stage: it defines the
`AudioBuffer` envelope and the two narrow manager interfaces the generator and
speaker depend on. Being an `:api`, it carries interfaces and types only — depend
on it to produce or consume audio; the production implementation lives in
`:cbox:common:player:buffer:real` and is wired by `:cbox:common:player:buffer:di`.

## Contents

| File | What it is |
| --- | --- |
| `AudioBuffer.kt` | One chunk of interleaved stereo 16-bit PCM (`data: ShortArray`) plus its `trackId`, `sampleRate`, `frameIndex`, fade params (`fadeStartMs`/`fadeLengthMs`) and loudness (`loudnessLufs`/`truePeakDbtp`). The array is pool-owned and recycled. |
| `ProducerBufferManager.kt` | Generator-facing interface: `setSampleRate`, `getNextEmptyBuffer`, `sendAudioBuffer`, `reset`. Borrow an empty array, fill it, send it; suspends as flow control when the queue is full. |
| `ConsumerBufferManager.kt` | Speaker-facing interface: `checkForNextAudioBuffer` (non-blocking poll), `waitForNextAudioBuffer`, `recycleShortArray`, `drain` (flush on seek). |
| `BufferDebugInfo.kt` | Observational snapshot of pool health (capacity, queued/empty counts, drain count). |
| `BufferDebugSource.kt` | `debugInfo(): StateFlow<BufferDebugInfo>` — read-only diagnostic view, kept separate from the two pipeline interfaces. |

## Why depend on this module

Depend on `:buffer:api` when your code produces or consumes pipeline audio: the
`Generator` takes a `ProducerBufferManager`, the `Speaker` takes a
`ConsumerBufferManager`, and both pass `AudioBuffer` around. The producer/consumer
split is deliberate — each side is injected the narrower interface so neither can
call the other's operations. Depend on `:api` for the types; the app gets the
backing `RealBufferManager` from `:real` wired through `:di`.

## Using it

```kotlin
// Producer side (generator):
producer.setSampleRate(44_100)
val array = producer.getNextEmptyBuffer()   // borrow a recycled, zeroed array
// ... fill `array` with interleaved L/R samples ...
producer.sendAudioBuffer(
    AudioBuffer(
        trackId = trackId,
        sampleRate = 44_100,
        frameIndex = frameIndex,
        data = array,
        fadeStartMs = fadeStartMs,
        fadeLengthMs = fadeLengthMs,
    ),
)

// Consumer side (speaker):
val buffer = consumer.checkForNextAudioBuffer() ?: consumer.waitForNextAudioBuffer()
// ... write buffer.data to the sink ...
consumer.recycleShortArray(buffer.data)      // hand the array back to the empty pool
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `kotlinx.coroutines.core` (`api`); none on other project modules (leaf within the pipeline)
