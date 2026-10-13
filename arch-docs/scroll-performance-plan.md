# Scroll performance — audit findings & action plan

Status: **#2, #3 and #4 landed**; #1, #5 open. One side-finding (the
`paparazzi-diff.sh` AE bug) also landed.

This doc owns the follow-ups from the scroll-performance audit of the debug
Android build. It records the baseline numbers, the evidence for *why* the UI
thread is the bottleneck, and the concrete action items (with status) so the
work can continue without re-deriving the analysis.

## Audit baseline

Device: Pixel 6a (`bluejay`), Android 13 (SDK 33), 60 Hz, over `adb`.
Target: `net.sigmabeta.chipbox.debug` (debuggable, not profileable).
Reference: installed release `net.sigmabeta.chipbox` (v3.0.0-beta14; different
code + library data — reference only, **not** a clean A/B).

Method: `dumpsys gfxinfo` (janky % + percentiles) driven by a continuous
on-device fling loop; `dumpsys gfxinfo framestats` for per-frame phase
attribution; `atrace -a <pkg> gfx view dalvik sched` reconstructed per-thread
for root cause.

| Surface | Release warm | Debug warm | Debug cold |
| --- | --- | --- | --- |
| Home | 5.8% | 9.4% | 26.9% |
| Browse by Game (grid) | 3–5% | 10–41% (unstable) | 17.6% |
| Browse All Tracks (text) | ~4.2% | **~38%** | 32.9% |

(Janky = frame > 16.7 ms.) Debug percentiles on the text list: p50 25–44 ms,
p90 73–109 ms, p99 129–150 ms.

> Debug caveat: the debug tail is inflated by ART `VerifyClass`, runtime JIT
> baseline compilation of the composition methods, and `Lock contention on Jit
> code cache` — all debug-only. Absolute debug numbers are an upper bound.

## Why the UI thread (root cause)

Main-thread bound. RenderThread self ≈ 2.2 ms/frame; GPU p50 1–3 ms — GPU is not
the problem. Main-thread self-time leaders over 349 grid frames:
`Compose:recompose` 1506 ms, `AndroidOwner:measureAndLayout` 1201 ms,
`animation` 438 ms, `AndroidOwner:draw` 423 ms, `onTouch` 353 ms.

The ~50 ms stall frames are a `Choreographer#doFrame` whose
`AndroidOwner:measureAndLayout` is ~45 ms, and *that* contains
`Compose:recompose` ~20 ms + `Compose:applyChanges` ~8–11 ms + text measure
~4 ms. In other words: **Compose recomposing during the lazy-layout measure
pass** — composing the newly-visible cells — plus always-present subcompositions
(`CrossfadeText`'s `AnimatedContent`, `CrossfadeImage`'s `Crossfade`).

## Action items

### 1. Profile on a release/profileable build — OPEN

**Why.** Debug is an upper bound; the tail (JIT/verification) disappears in
release. Most of the ~38% warm jank on the text list is debug overhead, not
inherent layout cost.

**How.** Build the `:apps:android` `benchmark` variant (release-based,
profileable) and run the existing `:benchmark` macrobenchmark
(`GridImageScrollBenchmark`) against it; or install a release build and rerun
the `dumpsys gfxinfo` harness. `:benchmark` currently targets `net.sigmabeta.chipbox`;
point the harness at whichever package/install carries the library data.

**Verification.** Debug-vs-release delta on the same surface/device, same
harness. Expect the p99 tail to collapse.

### 2. Cut recomposition depth per cell/row — DONE (`15660792`)

**Why.** `Compose:recompose` self-time dominated. Each row/cell ran
`animateColorAsState`/`animateIntAsState` at row scope (colour re-read at
composition every animation frame; `FontWeight` re-measured the text every
frame) and wrapped every text in `CrossfadeText` (`AnimatedContent`, a
subcomposition that never actually played during scroll).

**What landed.**
- `subs/TintedText.kt` — paints a `State<Color>` in the draw phase, so an
  animating colour never recomposes or re-lays-out the text.
- `subs/TintedIcon.kt` — the icon equivalent (leaf-isolated tint; icons can't
  defer without changing how Material `Icon` rasterises).
- Snap `FontWeight` instead of animating it.
- Replace `CrossfadeText` with `TintedText` (animated colour) / plain `Text`
  (static colour) across the list-item renderers.
- `CrossfadeImage` only wraps in a `Crossfade` after a real fetch; memory-cache
  hits hard-cut (skip the wrapper).
- `subs/LabeledThingy` now takes `State<Color>` for its label.

Applied to `GridImage`, `NameCaptionValueListItem`, and the sibling renderers in
`:cbox:common:ui:components:api` (see the commit for the full list). Left alone:
`NowPlayingHomeCard`, `ScanStatusCard` (bespoke card animations).

**Verification.** Compile (JVM + Android), ktlint; Paparazzi reports **no
divergence** (at-rest pixels identical). Re-run the on-device harness against a
fresh debug install to quantify (not yet done).

### 3. Re-evaluate the per-cell `CompositingStrategy.Offscreen` — DONE (`d97c5338`)

**Why.** `GridImage` wrapped every cell in an offscreen compositing layer
(introduced for the JS/Skiko grid-scroll win, `5645ecf2`). On Android it
allocated an extra GPU surface per visible cell (gfxinfo showed 169 image + 201
scratch surfaces, 61 MB each) while the hardware renderer already caches each
cell's `RenderNode`.

**What landed.** New `gridCellOffscreenLayerEnabled` expect/actual — `true` on
`jvm`/`js` (Skiko), `false` on `android`. Goldens re-recorded (<0.3% pixel
dithering/AA).

**Note.** The audit measured GPU/RT as *cheap*, so this was a memory/surface
cleanup more than a frame-time win. If a future Android GPU-bound regression
appears, re-check this (and the `ElevatedRoundRect` shadow) before adding more
layers.

### 4. Stop rebuilding the full item list per state emission — DONE (`8feff1fb`)

**Why.** `toListItems()` maps the *entire* paged window to `List<ListModel>` on
every call, and `ListState.toActual()` then filters + `toImmutableList()` +
`checkForDupes()` (a `groupingBy` over the whole window) on every state
emission. Pagination bursts (especially the first scroll) pay this
synchronously on the UI thread. Cold-start jank on the text list (release 24%,
debug 33%) is consistent with this.

**Chokepoints.**
- `features/browse-all-tracks/real/.../BrowseAllTracksState.kt` —
  `content()` = `tracks.mapIndexed(::trackRow)` over the whole window.
  (Same shape in the other `*State.toListItems()` overrides.)
- `sage/common/list/.../ListState.kt` — `toActual()` filter + `toImmutableList`
  + `checkForDupes`.
- `sage/common/list/.../ListUtils.kt` — `checkForDupes` (`groupingBy`).
- `cbox/common/ui/list/api/.../ChipboxListViewModel.kt` — `.map { it.toActual(...) }`
  on every emission.

**What landed.** The plan's original "How" (reduce the per-emission work) was
set aside for a cheaper, more general fix: the derivation ran on the UI thread.
Both base ViewModels now `flowOn(Dispatchers.Default)` between the `toActual`
`map` and `stateIn`, so the window remap never blocks a frame — for every list
and freeform screen, not just Browse All Tracks. The `Eagerly` seed
`initialValue` is still computed synchronously, so first composition keeps a
populated state. This reduces the *stall*, not the *work*; the per-emission
cost items below are still candidate follow-ups if the off-thread derivation
itself shows up in profiles.

- `cbox/common/ui/list/api/.../ChipboxListViewModel.kt`
- `cbox/common/ui/freeform/api/.../ChipboxFreeformViewModel.kt`

**Verification.** Full `:cbox:common:uitest` suite green over 3 consecutive
runs (89 tests); `:features:library:real` / `:features:browse-all-tracks:real`
unit tests green; ktlint + detekt clean; Paparazzi **no divergence** (at-rest
pixels identical). On-device before/after on the harness (below) not yet run.

**Still open (candidate follow-ups).**
- `checkForDupes` is always-on and allocates a whole-window count map. The
  "debug/assertion-only" idea from the original How needs a debug flag reachable
  from `sage/common/list` (which has no test source set and no `AppInfo`
  access) — and it's a graceful-error guard, not merely redundant with the
  `dataId` Lazy keys (gating it trades an `ErrorStateListModel` fallback for a
  hard LazyColumn duplicate-key crash in release). Keep that tradeoff in mind.
- Memoize/lazily derive `ListModel`s, or avoid the whole-window
  `toImmutableList()`, if profiling shows the off-thread derivation is itself
  the cost.

### 5. Fix the `:benchmark` trace-section names — OPEN

**Why.** `benchmark/src/main/.../GridImageScrollBenchmark.kt` records
`TraceSectionMetric("GridImage")` and `("CrossfadeImage")`, but no such
`trace { }` sections exist in the code (`rg` finds none), so those metrics
measure 0. The benchmark's real signal is currently only `FrameTimingMetric`.

**How.** Either add `trace("GridImage")` / `trace("CrossfadeImage")` sections
(via `:cbox:common:perf:api`) at the relevant composition sites, or change the
benchmark to metric sections that actually exist. Keep the README in sync.

**Verification.** `./gradlew :benchmark:connectedBenchmarkAndroidTest` on a
device; assert the section metrics are non-zero.

### Side-finding — `paparazzi-diff.sh` AE parsing — DONE (`a12283f4`)

`compare -metric AE` prints `"<count> (<normalized fraction>)"`; the ranker's
greedy `[0-9]+ | tail -1` grabbed the normalized parenthetical's digits and
reported a 0.09% diff as 148%. Fixed to take the leading count token. (Found
while validating #3's golden churn.)

## Ordering / dependencies

- #1 is a measurement enabler — do it first so #4 (and any further #2 work) is
  judged on release-representative numbers.
- #2, #3 and #4 are independent and landed.
- #4 landed as an off-thread derivation (`8feff1fb`); the per-emission work
  reductions remain candidate follow-ups if profiling calls for them.
- #5 is independent, low-risk dev-tooling.

## Re-running the audit

The harness used here (host scripts under `/tmp/opencode/chipbox-perf/` at audit
time) is not committed. To reproduce: pin the screen on (`svc power stayon
true`), launch the app, navigate to a surface, `dumpsys gfxinfo <pkg> reset`,
run a continuous on-device fling loop (`input swipe`), then dump
`dumpsys gfxinfo` (summary) and `dumpsys gfxinfo <pkg> framestats` (per-frame).
For root cause: `atrace --async_start -a <pkg> gfx view dalvik sched`, scroll,
`--async_stop`, and reconstruct per-thread B/E slices. Consider committing a
small version of this harness so the comparison is repeatable.
