# `:apps:android`

> The Android app — the runnable APK that assembles every feature/`:di` module into one Metro `@DependencyGraph(AppScope)`.

The top of the Android dependency graph: a single-Activity Compose app that wires
the full Chipbox stack (repository, scanner, playback pipeline, history,
favorites, playlists, settings, content source) through Metro and renders the
shared `ChipboxAppUi`. Nothing depends on this module — it's the leaf that
produces the installable app.

## What this builds / how to run it

- **App target:** the `net.sigmabeta.chipbox` Android application (`minSdk 26`,
  `targetSdk 36`). Debug installs as `net.sigmabeta.chipbox.debug` so it can live
  alongside a release build; the artwork `ContentProvider` authority is derived
  from the applicationId at runtime so the two installs don't collide.
- **Build:** `./gradlew :apps:android:assembleDebug` (the debug APK; `release`
  and a profiling `benchmark` variant are also configured). Release version
  code/name are derived from the latest git tag via the `git.version` plugin
  (release builds only; debug uses the `versionCode = 1` / `0.1.0` fallbacks).
- Don't boot an AVD to verify — build + lint, then hand device testing to the user.

## Entry points

- `ChipboxApplication` — the `Application`. Owns the `appGraph` (lazy
  `ChipboxAppGraph`), installs the crash handler in `onCreate`, sets the artwork
  provider authority in `attachBaseContext`, and implements the narrow
  `ArtworkProviderGraph` / `ChipboxServiceGraph` faces that the
  `ContentProvider` / playback service cast to.
- `MainActivity` — the single `ComponentActivity`. Installs the splash, enables
  edge-to-edge, provides `LocalMetroViewModelFactory` / `LocalChipboxStringProvider`
  / `LocalLogger`, mounts `ChipboxAppUi`, binds the `MediaController` to
  `ChipboxPlaybackService` in `onStart`, and routes hardware back keys
  (Escape/Backspace) into the shell.

## The graph it assembles

`ChipboxAppGraph` (`di/ChipboxAppGraph.kt`) is the `@DependencyGraph(AppScope::class)`
root — it extends `ViewModelGraph` so `metroViewModel<T>()` resolves, exposes the
accessors the entry points read (`repository`, `director`, `libraryBrowser`,
`crashReporter`, etc.), and binds `Context` from `Application`. `AndroidAppModule`
is the `@ContributesTo(AppScope::class)` `@BindingContainer` supplying the
Android-specific bindings (`AndroidHatchet`, `NoopAnalytics`, `AppInfo` from
`BuildConfig`, crash dir/reporter/store, `ChipboxStringProvider`,
`LocalFileContentSource` as `LibrarySource`, `FileSystem.SYSTEM`). Every other
binding comes from the contributed `:di` modules listed in `dependencies {}`.

## Contents

| File | What it is |
| --- | --- |
| `ChipboxApplication.kt` | `Application`; owns `appGraph`, installs crash handler, sets artwork authority. |
| `MainActivity.kt` | Single `ComponentActivity`; mounts `ChipboxAppUi`, binds the media service. |
| `di/ChipboxAppGraph.kt` | `@DependencyGraph(AppScope)` root extending `ViewModelGraph`. |
| `di/AndroidAppModule.kt` | `@ContributesTo(AppScope)` `@BindingContainer` with Android-specific bindings. |
| `di/ChipboxMetroViewModelFactory.kt` | Metro-backed `ViewModelProvider.Factory` for `metroViewModel<T>()`. |
| `src/main/AndroidManifest.xml` | Permissions (storage, foreground-service media playback), the launcher activity, Android Auto descriptor. |
| `src/main/res/`, `src/debug/res/` | Launcher icons, themes, colors, splash theme, strings. |

## Module facts

- **Plugin:** `android.application` + `compose.compiler` + `metro` + `detekt` +
  `git.version`. Metro runs `interop.includeDagger()` so the Dagger-shaped
  `@Inject`/`@Provides`/`@Module` annotations on contributed modules keep working.
- **Targets:** Android only.
- **Source set:** `src/main/kotlin` (+ `src/main/res`, `src/debug/res`).
- **SAGE/module dependencies:** the full app composition — feature `:api`/`:real`,
  the shared `cbox/common/*` and `cbox/android/*` `:di`/`:api` modules (database,
  repository, scanner, player chain, history, favorites, playlists, settings,
  crash, strings, services, artwork provider, debug), plus `sage.common.*` /
  `sage.android.*` (di, list, appcomm, analytics, logging, coroutines, ui themes,
  appinfo) and AndroidX Compose / Activity / Lifecycle / splash and `metrox.viewmodel`.
