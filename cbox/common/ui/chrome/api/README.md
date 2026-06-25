# `:cbox:common:ui:chrome:api`

> Top-bar / nav-chrome controllers and the per-screen `ChipboxEvent` sink.

The shared UI-chrome contract: tiny mutable-state controllers that a feature
screen mutates to drive the app shell's `TopAppBar`, navigation bar, and
mini-player, plus the composition local screens use to emit navigation/system
events. As an `:api` module it holds only the public types and composition
locals; the shell that reads them lives in `:cbox:common:appui:api`.

## Contents

| File | What it is |
| --- | --- |
| `ScreenChrome.kt` | Immutable `data class ScreenChrome(showTopBar, showNavBar, showPlayerStatus)` with a `Default` (all `true`). The desired shell chrome for the visible screen. |
| `ChromeController.kt` | `@Stable ChromeController` holding a `ScreenChrome` snapshot state (`set(...)` mutates it) + `LocalChromeController`. A screen sets this to hide bars (e.g. NowPlaying hides the top bar and mini-player). |
| `TitleBarController.kt` | `@Stable TitleBarController` holding a SAGE `TitleBarModel(title, shouldShowBack)` snapshot state + `LocalTitleBarController`. Drives the `TopAppBar` title text and back/menu icon. |
| `LocalChipboxEventSink.kt` | `LocalChipboxEventSink: (ChipboxEvent) -> Unit` — the per-screen sink feature VMs emit into. Refined per Voyager tab so `NavigateTo`/`NavigateBack` stay in the tab while system events bubble up. |

## Why depend on this module

Depend on `:cbox:common:ui:chrome:api` when a screen or shell component needs to
read or mutate the app's chrome (top bar / nav bar / mini-player visibility,
title-bar text) or needs the `ChipboxEvent` sink. Feature `:real` modules use
the controllers to customize chrome; the app shell (`:cbox:common:appui:api`)
provides all four composition locals and renders against their state. The
controllers are plain `@Stable` classes with no DI — they're hoisted by the
shell via `remember { … }` and provided through composition locals.

## Using it

```kotlin
// Inside a feature Route: hide the top bar and mini-player for this screen,
// and set the title-bar text + back affordance.
@Composable
fun NowPlayingRoute(onEvent: (ChipboxEvent) -> Unit) {
    val chrome = LocalChromeController.current
    val titleBar = LocalTitleBarController.current
    LaunchedEffect(Unit) {
        chrome.set(ScreenChrome(showTopBar = false, showPlayerStatus = false))
        titleBar.set(TitleBarModel(title = "Now Playing", shouldShowBack = true))
    }
    // …emit navigation/system events through the per-screen sink:
    // onEvent(ChipboxEvent.NavigateBack)
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `sage.compose.kmp`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` only
- **SAGE/module dependencies:** `sage.common.ui.components` (`TitleBarModel`),
  `:cbox:common:appcomm:api` (`ChipboxEvent`) — both exposed via `api()`.
