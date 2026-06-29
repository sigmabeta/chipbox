# Playback system

How a file becomes audio. The pipeline is a set of independent stages connected
by coroutine channels and event flows; the Director is the only piece that knows
about all of them. All interfaces are in `cbox/common/player/*/api`; impls in
`*/real`; DI in `*/di`. Most of it is `commonMain` (runs identically headless on
JVM); only the speaker sink and the native cores are platform-specific.

```
play(Session)
   │
RealDirector ── orchestrates, holds the state machine ──────────────┐
   │ start/stop/seek/skip                                           │
   ▼                                                                │
RealGenerator ── decode loop on IO ──► PcmTrackSource               │ merges
   │   picks Emulator by extension     (cache hit = file reader,    │ Generator.events()
   │   hashes source → PcmCacheKey       miss = live emulator +     │ + Speaker.events()
   │                                     render-ahead writer)       │ → reduce() → PlayerState
   ▼ AudioBuffer                                                     │ → ChipboxPlaybackState
RealBufferManager ── bounded ShortArray pool (empty/full channels) ─┤
   ▼ AudioBuffer                                                     │
BaseSpeaker consume loop ──► onAudioReceived()                      │
   │  RealSpeaker: AudioTrack (Android) / SourceDataLine (JVM)      │
   │  applies fade + loudness normalization                         │
   └────────────────── Speaker.events() ───────────────────────────┘
```

## Director — `cbox/common/player/director`

`Director` interface exposes four hot flows the UI/service observe:
`metadataState(): SharedFlow<Track?>`, `playbackState(): SharedFlow<ChipboxPlaybackState>`,
`sessionState(): SharedFlow<Session?>`, `errorEvents(): SharedFlow<PlayerErrorEvent>`.

`RealDirector` runs on a single-threaded dispatcher
(`Dispatchers.Default.limitedParallelism(1)`), so its `Model` mutates without
locks. It **merges** `Generator.events()` and `Speaker.events()` and runs pure
reducers `reduce(model, event) → (nextModel, effects)`; effects (StartTrack,
StopGenerator, SwitchSpeaker, ArmWatchdog, EmitMetadata, PublishError…) are then
executed. Transport methods (`play/pause/stop/seek/skipForward/skipBack/
setShuffled/duck/setVolume`) are imperative entry points that launch onto the
director scope. It owns track sequencing/shuffle, the IDLE→BUFFERING→PLAYING→
ENDING→STOPPED/ERROR transitions, and a stall watchdog (≈5 s) that synthesizes
an error if the generator goes silent.

**Start-ordering constraint**: the speaker consume loop must start *before* the
generator emits its first `Emitting`. Start the speaker first, then the generator,
so the `Buffering` state can clear cleanly and the consumer doesn't spin on a
pre-`setSampleRate` channel that the buffer manager swaps out.

## Generator — `cbox/common/player/generator`

`Generator`/`BaseGenerator`/`RealGenerator` run a single decode loop on the IO
dispatcher. The loop pulls a track id from a 1-slot `Channel<Long>`, opens a
`PcmTrackSource`, calls `setSampleRate` on the buffer manager, then repeatedly
reads frames into borrowed `ShortArray`s and sends `AudioBuffer`s downstream.
Each iteration emits a `GeneratorEvent`:

- `Loading(trackId)` — before render starts.
- `Emitting(producedMs, trackId, cachedMs)` — a buffer was produced;
  `producedMs` is the generator's watermark, `cachedMs` is how much is on disk.
- `Rendering(cachedMs)` — reader is ahead of the cache writer; waiting.
- `TrackChange` — current track ended (a `data object`).
- `Error(message)` — terminal (e.g. "cache writer fell behind reader" on heavy
  cores with a cold cache).

## Emulators — `cbox/common/player/emulators` + `cbox/native/*`

`Emulator` (abstract) defines `loadNativeLib`, `loadTrackInternal(path)`,
`generateBufferInternal(buffer, frames): Int`, `teardownInternal`,
`getSampleRateInternal`, `getLastError`, plus a `supportedFileExtensions` list.
The base class tracks remaining frames (track length + fade) and flips
`trackOver`. `EmulatorProvider` holds the list; the cache factory picks the first
emulator whose extension matches.

Nine cores, each an `object` JNI wrapper (`external` funs + `System.loadLibrary`):
GME (spc/nsf/nsfe/gbs), VGM (vgm/vgz), PSF (psf/minipsf/psf2…), USF (usf/miniusf),
SSF/DSF, 2SF (`twosf`), NCSF, GBA/mGBA (gsf), vgmstream (many streamed formats).
Each has a KMP `:real` wrapper (`cbox/common/player/emulators/<emu>/real`) plus an
Android-only `:native` CMake companion that packs the `.so`; the JVM target
host-builds the same `cbox/native/<emu>` tree into `apps/jvm/libs`. See
`apps/jvm/README.md` for the per-emulator host-build table.

## Cache + render-ahead — `cbox/common/player/cache`

`RealPcmTrackSourceFactory.open(track, bytes)` hashes the source + chain files
into a `PcmCacheKey(sourceHash, trackNumber, sampleRate)` and returns one of:

- `CachedFilePcmSource` — cache **hit**: a complete `.pcm` file; instant seek.
- `CachingPcmSource` — cache **miss**: a writer coroutine runs the emulator
  ahead, appending to a `.pcm.tmp` while the generator's reader trails it on the
  same file. A watermark `StateFlow<Long>` reports frames written;
  `awaitingRender` is true when the reader passes the writer. On completion the
  `.pcm.tmp` is atomically renamed to `.pcm`. Loudness (BS.1770) is integrated
  during the write and used by the speaker to normalize.
- `EmulatorPcmSource` — bare emulator, no caching/seeking.

`PcmTrackSource` interface: `sampleRate`, `totalFrames`, `cachedFrames`,
`awaitingRender`, `loudnessLufs`/`truePeakDbtp`, `suspend readFrames(buffer): Int`,
`suspend seek(frame)`, `isOver`, `getLastError()`.

## Buffer — `cbox/common/player/buffer`

`RealBufferManager` implements both `ProducerBufferManager` and
`ConsumerBufferManager` over a swappable `Pool` of two channels: `empty:
Channel<ShortArray>` (producer borrows arrays) and `full: Channel<AudioBuffer>`
(consumer receives envelopes). `setSampleRate` atomically swaps the pool (sized
to ~500 ms of audio). Backpressure is natural: the producer blocks on
`sendAudioBuffer` when full, the consumer blocks on `waitForNextAudioBuffer` when
empty. `AudioBuffer` carries `trackId`, `sampleRate`, `frameIndex`, the
interleaved L/R `ShortArray`, fade params, and loudness/peak.

## Speaker — `cbox/common/player/speaker`

`Speaker`/`BaseSpeaker` run a single consume loop: poll (non-blocking) → on empty
emit `SpeakerEvent.Buffering` then block; detect track boundaries (emit
`TrackChange`, discard buffers for stale tracks); apply fade + normalization;
write to the sink; recycle the array; emit `SpeakerEvent.Playing(positionMs)`.

Sinks (platform seam):
- `RealSpeaker` (androidMain) — `AudioTrack`; re-inits on sample-rate change;
  position from `playbackHeadPosition`.
- a JVM `SourceDataLine` impl (jvmMain).
- `FileSpeaker`/`TextSpeaker` fakes — WAV file / stdout level meter (headless).

`SpeakerEvent`: `Buffering(positionMs)`, `Playing(positionMs)`,
`TrackChange(trackId)`, `Error(message)`.

## UI observation — `cbox/common/player-status`

`PlayerStatusViewModel` injects the `Director` and `combine`s
`metadataState()` + `playbackState()` into a `StateFlow<PlayerStatusState>`
(visible/isPlaying/isBuffering/isError + title/artists/artwork). The mini-player
and now-playing screens observe this. `ChipboxPlaybackState` carries
`state: PlayerState`, `position`, `generatorProducedMs`, `cachedMs`,
`skipForwardAllowed`, `errorMessage`.

## Threading summary

| Stage | Dispatcher | Invariant |
|---|---|---|
| RealDirector | `Default.limitedParallelism(1)` | single thread → lock-free `Model` |
| Generator loop | IO | one loop coroutine; no concurrent `readFrames` |
| Cache writer (CachingPcmSource) | IO | separate from the reader |
| BufferManager | caller's | atomic `Pool` swap; `@Volatile` |
| Speaker consume loop | `Default` | one loop; no concurrent sink writes |

> The internal constants/dispatcher names above were read from the impls at doc
> time — verify at the source files in `cbox/common/player/*/real` before relying
> on an exact number.
