# AGENTS.md — Chipbox orientation

Orientation for coding agents. Keep this file lean; deep dives live in
`arch-docs/architecture/` and are loaded on demand. Update this file when a
load-bearing fact below stops being true.

## What Chipbox is

A chiptune jukebox: it plays raw sound-chip program/data dumps (SPC, RSN, NSF,
VGM, PSF, USF, SSF, 2SF, NCSF, GSF, plus vgmstream formats) by emulating the
original hardware in real time. Kotlin Multiplatform, Compose Multiplatform UI, targeting
Android (minSdk 26 / targetSdk 36) and desktop JVM, with `js`/`cli`/`server`
targets too.

## Before doing any work

Pull the latest `sage/` submodule first: `git -C sage pull` (or
`git submodule update --remote sage`). Chipbox builds against sage source, so a
stale checkout means you're working against outdated APIs and build logic. If the
latest tip breaks the build, flag that before doing other work.

Run `scripts/dev/doctor.sh` on a new machine: it reports which toolchains exist
(JDK, node/yarn, emsdk, cmake, Docker, Android SDK) with the fix for each, and
which `scripts/ci/verify.sh` axes are runnable here.

## The four things I keep re-deriving (read these first)

- `arch-docs/architecture/multiplatform-structure.md` — module taxonomy, source sets
  (`commonMain` vs `jvmSharedMain`=`src/main/java`), convention plugins, how to
  add a module, native build.
- `arch-docs/architecture/feature-screens.md` — anatomy of a `features/*` screen
  (api/real/screenshot, `ChipboxListViewModel`, `ListState`/`ListModel`,
  Voyager route registration). Start here to build/modify a screen.
- `arch-docs/architecture/playback-system.md` — Director → Generator → Emulator →
  Cache → Buffer → Speaker pipeline and the events between them.
- `arch-docs/architecture/sage-integration.md` — what the `sage/` submodule provides
  and the chipbox↔sage type/DI/build boundary.

## Per-module READMEs

Every Gradle module — chipbox **and** the `sage/` submodule — has a `README.md`
at its root: what it contains, why you'd depend on it, and how to use its
code/resources (per-file for small modules, grouped by role for large ones).
**Before working in a module, read its README first**, then skim the READMEs of
the modules it depends on — it's the fastest orientation to a module's public
surface and its api/real/di/fake siblings. Keep a module's README in sync when
you change its public API, dependencies, or role — same rule as this file: update
it when a load-bearing fact stops being true.

## Load-bearing facts (verified 2026-05-31)

- **DI is Metro** (`dev.zacsweers.metro`), not Hilt. Hilt + KSP were removed
  (Milestone 6). Metro keeps Dagger-shaped
  annotations working via `metro { interop.includeDagger() }`. App graph:
  `@DependencyGraph(AppScope::class)` in `apps/android`. ViewModels:
  `@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())` +
  `@ViewModelKey` + `@Inject`; resolve with `metroViewModel<VM>()`. `AppScope`
  lives in `sage/common/di` (referenced by FQCN).
- **Navigation is Voyager** (`cafe.adriel.voyager`), not AndroidX Nav (removed).
  Route keys are `@Serializable data object`/`data class` in each feature's
  `:api`; `cbox/common/appui/api/.../ChipboxScreens.kt` maps them to Voyager
  `Screen`s via `screenFor(destination)`. There is no `NavHost`/`NavBackStackEntry`
  /`SavedStateHandle.toRoute()` — route args are passed to assisted VM factories
  as typed values.
- **KMP source sets**: pure Kotlin → `src/commonMain/kotlin/`. Code that needs
  `java.*` → `src/main/java/` (mapped to the `jvmSharedMain` intermediate source
  set, which both `androidMain` and `jvmMain` depend on). Platform-only →
  `src/androidMain` / `src/jvmMain`. Converting a module to KMP needs **no file
  moves**; hoist to `commonMain` incrementally.
- **Module suffixes**: `:api` (interfaces/types/route keys, KMP), `:real`
  (impl), `:di` (Metro bindings), `:fake` (test doubles), `:native`
  (Android-only CMake companion for a JNI core), `:screenshot` (Paparazzi).
- **Scanning is pure Kotlin; playback is native.** A library scan parses
  chiptune and in-scope streamed headers in `:cbox:common:readers:api` and never
  loads `libvgmstream`; playback (GUI/Android/JS) still does. Unknown streamed
  extensions are skipped, counted and warned about, not silently dropped. To add
  a streamed format see `arch-docs/architecture/streamed-audio-readers.md`.
- The app is fully KMP: every `features/*` and shared `cbox/common/*` module
  builds for both targets. The only modules that stay non-KMP are legitimate
  end-state glue — `:di` wiring, `:native` (CMake) + `:screenshot` (Paparazzi)
  companions, and Android system modules (audio service, SAF/ContentProvider,
  `R.*` resources, Android Main dispatcher). Remaining KMP-adjacent work:
  macOS desktop native builds (Linux runs natively; Windows is cross-compiled via
  MinGW-w64 with `-Pchipbox.jvm.nativeTarget=windows-x64` — see `Readme.md`).

## Build / verify

```sh
./gradlew :apps:android:assembleDebug      # Android build
./gradlew :apps:jvm:run --args="gui"       # desktop window
./gradlew ktlintCheck detekt               # lint — run BOTH at the same scope
./gradlew ktlintFormat                     # auto-fix
scripts/dev/paparazzi-diff.sh                  # check screenshots (use this, NOT the Gradle task)
scripts/dev/build-benchmark.sh                 # time this machine's builds (--quick / --full / --help)
scripts/ci/verify.sh --device-audio <corpus>  # also run + gate ONE on-device axis (also -ui / -startup; needs a device; small corpus)
```

- Run **ktlint and detekt together**; both gate commit/push.
- Gradle prints thousands of task lines: redirect a long run to a file and grep it
  (`./gradlew … > /tmp/build.log 2>&1`) rather than pulling them into context.
- **Always verify screenshots via `scripts/dev/paparazzi-diff.sh`**, never by invoking
  the Gradle tasks directly: it re-renders, ranks the most-divergent snapshots,
  writes `golden | new | diff` montages under `build/paparazzi-review/`, and
  reverts the goldens afterward (non-destructive). After running it, **report a
  summary back**: how many goldens diverged, the worst AE% offenders, and whether
  the change is expected — note that `--top N` only ranks the loudest N, so cross-
  check the full set with `git status` rather than trusting the ranked list as the
  total.
- Under the hood `verifyPaparazziDebug` *checks* goldens and `testDebugUnitTest`
  *records* (overwrites) them (git-LFS) — only re-record (`--keep`, or the record
  task) when a visual change is intentional.
- **Never create or boot an emulator/AVD.** A **physical device already on `adb`**
  is fair game — run the device checks on it. The device recipes (corpus + snapshot
  push, `ANDROID_SERIAL` pinning, the duplicate-device trap, the arm64-only benchmark
  APK, per-scenario tuning) are in `benchmark/README.md`; background in
  `arch-docs/scroll-performance-plan.md`. Hand off only what has no attached
  hardware. Don't `git commit`/`push` unless explicitly asked.
- Always run `scripts/ci/verify.sh` (the full CI-mirroring suite — lint, unit tests,
  Paparazzi, shared build, android-lint, desktop natives, release APK) before any `git
  push`, and only push if it reports `OVERALL: PASS`. Heed its `WARNING:` lines too
  (uncommitted changes, unpushed `sage` commit) — they mean CI may not see what you tested.
- A/B-testing a native emulator change (USF/PSF/SSF/…): `testing/abrender` renders a corpus to WAV + RMS
  metrics and diffs two runs. Two scripts drive it, each taking `--where host|device` and `--corpus`:
  `scripts/abrender/record.sh` renders a run (host JVM or arm64 on a device), and
  `scripts/abrender/verify.sh` renders again and fails on any divergence beyond the tolerance (strict
  by default; `cross-isa`, a ±1 LSB allowance, when the reference and render came from different
  places). A recorded `reference` run is local + uncommitted. See `testing/abrender/README.md` and
  `testing/abrender-core/README.md`.
- Benchmarking a machine's build speed (new dev box, CI runner sizing):
  `scripts/dev/build-benchmark.sh` times a fixed set of real builds from scratch with the
  build cache off, prints a fingerprint of the box, and reduces it to one index. Compare
  two machines with `--baseline <results.json>` — never quote an index without its
  scenario set and `--repeat`. It is not part of CI; it is a measurement, not a gate.
- The on-device perf gates are `scripts/benchmark/device-<axis>.sh` (the
  `device-<axis>` tasks in `scripts/ci/verify.sh --device-<axis> <corpus-dir>`), one per
  axis (audio/ui/startup): the only tasks that aren't CI. Each runs just its axis's
  test class over the corpus you pass (use a **small** one — the audio axis renders
  every file) on **every attached device** (so no serial is pinned, which keeps the
  Gradle daemon in play), diffs each device against its own **local** baseline
  (`build/perfbench-baseline/device/<model>-<abi>/`), and fails on a regression or a
  test failure — but a missing device (or one attached over two transports) is a
  flagged `SKIP`, not a failure. One axis per process is deliberate: a later
  macrobenchmark would otherwise wipe an earlier non-macrobenchmark axis's result.
  See `benchmark/README.md`.

## UI tests

Cross-platform UI tests live in `:cbox:common:uitest` and drive the real Compose
UI over fakes. **Two rules for agents:**

- ✅ You **may add** new UI tests for new features/screens.
- ⛔ You **must not modify** an existing UI test — assertions, data, name, or
  structure — and never to make a failing one pass. The tests are the **source of
  truth**, owned by humans: if your change breaks one, **stop and report it**, don't
  edit it. Only a human changes a test (and may direct you to).

How to write one, the DSL verbs, and platform gotchas:
`arch-docs/architecture/ui-tests-for-agents.md`. Design/internals:
`arch-docs/architecture/ui-test-dsl.md`.

## Agent skills

### Issue tracker

Issues and specs live as markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles, default label strings (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
