# `:features:playback-status:real`

> The playback-diagnostics screen impl: a live dump of the audio pipeline.

The implementation behind the `PlaybackStatus` route. It collects a
`PlaybackDebugInfo` snapshot stream from `DebugInfoManager` and renders it as a
sectioned label/value list (playback, track, session, generator, speaker,
volume, resampler, buffer), plus a "copy debug info" CTA. As a feature `:real`
module it holds the `ChipboxListViewModel`, the `ListState`, the Compose
`Route` content, and the Metro DI wiring; the app pulls it in and
`cbox/common/appui/api`'s `ChipboxScreens` registers the route via
`screenFor()`.

This is the **debug-only diagnostics screen** (reached from the gated Settings
debug section), not a mini-player bar.

## Contents

| File | What it is |
| --- | --- |
| `PlaybackStatusRoute.kt` | `@Composable PlaybackStatusRoute(...)` — resolves the VM via `metroViewModel()` and renders it through `ChipboxListEntry`. |
| `PlaybackStatusViewModel.kt` | `ChipboxListViewModel<PlaybackStatusState>`; collects `DebugInfoManager.debugInfo()` into state and handles the copy-debug-info action. Wired with `@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())` + `@ViewModelKey` + `@Inject`. |
| `PlaybackStatusState.kt` | `ListState` subclass that folds a `PlaybackDebugInfo?` into the title bar and the eight diagnostic sections; includes the SAF-path shortener. |
| `PlaybackStatusAction.kt` | `sealed ChipboxAction` with the single `CopyDebugInfoClicked` action. |
| `UrlDecode.kt` (commonMain) | `internal expect fun urlDecodeUtf8(value: String)` — used by the state's path shortener. |
| `UrlDecode.kt` (`src/main/java`) | JVM/Android `actual`: delegates to `java.net.URLDecoder`. |
| `UrlDecode.kt` (`src/jsMain`) | JS `actual`: a best-effort manual percent-decoder for the enforcement-only target. |
| `PlaybackStatusViewModelTest.kt` (`commonTest`) | Covers snapshot collection and the copy-to-clipboard event, driving fakes. |

## The `urlDecodeUtf8` expect/actual split

The path-shortener in `PlaybackStatusState` decodes SAF content-tree paths
before trimming them to the trailing folder/filename. Because `commonMain` has
no URL decoder, this is an `expect`/`actual`:

- `commonMain` declares `internal expect fun urlDecodeUtf8(value: String): String`.
- `src/main/java` (the `jvmSharedMain` source set shared by `androidMain` and
  `jvmMain`) provides the real `actual` via `java.net.URLDecoder`.
- `src/jsMain` provides a best-effort ASCII percent-decode `actual`; the JS
  target exists only for cross-platform enforcement and never runs this screen.

Callers wrap the call in `runCatching`, so any decode failure falls back to the
raw path.

## Why depend on this module

Only the app graph depends on `:real` — it brings in the screen content and the
DI binding that registers `PlaybackStatusViewModel` into the app's ViewModel
map. Everything else should depend on `:features:playback-status:api` (the route
key) instead, so navigating to the screen doesn't pull in its implementation.

## Using it

```kotlin
// In ChipboxScreens.screenFor(): the route key maps to a Screen that renders
// the Route content; the VM is resolved from the Metro graph internally.
private object PlaybackStatusScreen : Screen {
    @Composable override fun Content() = ScreenScaffold {
        PlaybackStatusRoute(onEvent = LocalChipboxEventSink.current)
    }
}
```

## Module facts

- **Plugin:** `chipbox.feature.real` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (+ `src/main/java` / `src/jsMain` `actual`s for `urlDecodeUtf8`; `commonTest` for the VM test)
- **SAGE/module dependencies:** `api(:features:playback-status:api)`; `:cbox:common:appcomm:api`, `:cbox:common:ui:list:api`, `:cbox:common:strings:api`, `:cbox:common:models:api`, `:cbox:common:player:common:api`, `:cbox:common:debugInfo:api`, `:cbox:common:utils:api`, `sage.common.ui.components`; (test) `:cbox:common:debugInfo:fake`
