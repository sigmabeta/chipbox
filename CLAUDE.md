# CLAUDE.md — Chipbox orientation

Orientation for Claude Code. Keep this file lean; deep dives live in
`docs/architecture/` and are loaded on demand. Update this file when a
load-bearing fact below stops being true.

## What Chipbox is

A chiptune jukebox: it plays raw sound-chip program/data dumps (SPC, RSN, NSF,
VGM, PSF, USF, SSF, 2SF, NCSF, GSF, plus vgmstream formats) by emulating the
original hardware in real time. Kotlin Multiplatform, Compose Multiplatform UI, targeting
Android (minSdk 26 / targetSdk 36) and desktop JVM, with `js`/`cli`/`server`
targets too.

## The four things I keep re-deriving (read these first)

- `docs/architecture/multiplatform-structure.md` — module taxonomy, source sets
  (`commonMain` vs `jvmSharedMain`=`src/main/java`), convention plugins, how to
  add a module, native build.
- `docs/architecture/feature-screens.md` — anatomy of a `features/*` screen
  (api/real/screenshot, `ChipboxListViewModel`, `ListState`/`ListModel`,
  Voyager route registration). Start here to build/modify a screen.
- `docs/architecture/playback-system.md` — Director → Generator → Emulator →
  Cache → Buffer → Speaker pipeline and the events between them.
- `docs/architecture/sage-integration.md` — what the `sage/` submodule provides
  and the chipbox↔sage type/DI/build boundary.

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
- The app is fully KMP: every `features/*` and shared `cbox/common/*` module
  builds for both targets. The only modules that stay non-KMP are legitimate
  end-state glue — `:di` wiring, `:native` (CMake) + `:screenshot` (Paparazzi)
  companions, and Android system modules (audio service, SAF/ContentProvider,
  `R.*` resources, Android Main dispatcher). Remaining KMP-adjacent work:
  macOS/Windows desktop native builds (Linux-only today — see `Readme.md`).

## Build / verify (no device, no emulator boot)

```sh
./gradlew :apps:android:assembleDebug      # Android build
./gradlew :apps:jvm:run --args="gui"       # desktop window
./gradlew ktlintCheck detekt               # lint — run BOTH at the same scope
./gradlew ktlintFormat                     # auto-fix
./gradlew verifyPaparazziDebug             # check screenshots (does NOT record)
```

- Run **ktlint and detekt together**; both gate commit/push.
- `verifyPaparazziDebug` *checks* goldens; `testDebugUnitTest` *records*
  (overwrites) them (git-LFS). Use verify unless intentionally re-recording.
- Don't boot an AVD to verify — stop after build + lint and hand device testing
  to the user. Don't `git commit`/`push` unless explicitly asked.
- Always run `scripts/verify.sh` (the full CI-mirroring suite — lint, unit tests,
  Paparazzi, shared build, android-lint, jvm dist, debug APK) before any `git
  push`, and only push if it reports `OVERALL: PASS`.
- A/B-testing a native emulator change (USF/PSF/SSF/…): `apps/abrender` renders a
  corpus to WAV + RMS metrics and diffs two runs. See `apps/abrender/README.md`.
