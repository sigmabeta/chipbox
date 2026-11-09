# `:features:playback-status:real`

> The playback-diagnostics screen impl: a live dump of the audio pipeline.

> The screen's `State`/`Action` value types live in `:features:playback-status:api`; this module holds the ViewModel, Route and runtime helpers.

The implementation behind the `PlaybackStatus` route. It collects a
`PlaybackDebugInfo` snapshot stream from `DebugInfoManager` and renders it as a
sectioned label/value list (playback, track, session, generator, speaker,
volume, resampler, buffer), plus a "copy debug info" CTA. As a feature `:real`
module it holds the `ChipboxListViewModel`, the Compose
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
| `PlaybackStatusViewModelTest.kt` (`commonTest`) | Covers snapshot collection and the copy-to-clipboard event, driving fakes. |

The `urlDecodeUtf8` expect/actual used by the path-shortener lives with
`PlaybackStatusState` in `:features:playback-status:api`.

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
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (`commonTest` for the VM test); the state and `urlDecodeUtf8` actuals live in `:api`.
- **SAGE/module dependencies:** `api(:features:playback-status:api)`; `:cbox:common:appcomm:api`, `:cbox:common:ui:list:api`, `:cbox:common:strings:api`, `:cbox:common:models:api`, `:cbox:common:player:common:api`, `:cbox:common:debugInfo:api`, `:cbox:common:utils:api`, `sage.common.ui.components`; (test) `:cbox:common:debugInfo:fake`
