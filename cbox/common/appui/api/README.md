# `:cbox:common:appui:api`

> The Chipbox app shell — the cross-platform `ChipboxAppUi()` composable, its
> chrome/tab scaffold, and the Voyager screen registry that maps every feature
> route key to a `Screen`.

This is the host UI module shared by Android `MainActivity` and JVM
`DesktopMain`. It owns the outer Voyager `Navigator`, the three root tabs
(Home / Library / Search) with their per-tab back stacks, the `TopAppBar` +
`NavigationSuiteScaffold` + mini-player chrome, the back-routing logic (system
back, up arrow, mouse back-button, keyboard Escape/Backspace), and the
`screenFor(destination)` table that turns each feature's route key into a
Voyager `Screen`. Despite the `:api` suffix this is mostly composables and shell
logic, not pure types; it's the single place all features are wired together for
DI aggregation and navigation.

## Contents

Grouped by role (commonMain unless noted):

- **App composable** — `ChipboxAppUi.kt`: the public `ChipboxAppUi(...)` entry
  point. Resolves `ChipboxAppUiViewModel`, applies `AppTheme`, provides every
  composition local, owns the outer `Navigator(ChipboxTabsScreen)`, and drains
  the VM's `effects` flow into `buildOuterSink(...)` (snackbars, open-URL,
  clipboard, chrome toggles). Platform side effects (open URL, copy to clipboard)
  arrive as host-supplied callbacks so the composable stays in `commonMain`.

- **Shell / chrome** — `ChipboxNavHost.kt`: `ChipboxTabsScreen` (root of the
  outer Navigator) renders the `TopAppBar`, `NavigationSuiteScaffold`
  (bar vs. rail by width), mini-player overlay, the `TabNavigator`, the single
  `LocalAppActionSink` back handler, tab-switch animation (`AnimatedCurrentTab`),
  the nav items, and `buildOuterSink(...)`.

- **ViewModel** — `ChipboxAppUiViewModel.kt`: `@ContributesIntoMap` singleton VM
  that exposes theme mode, brand/plain fonts, `isDebugBuild`, and
  `forceFakeImages` as `StateFlow`s for the theme, tracks `currentRoute`, and
  forwards screen `ChipboxEvent`s through `handleEvent` → `effects`. Also
  declares `LocalChipboxAppUiViewModel` so the shell reaches the same instance.

- **Navigation / screen registration** — `ChipboxScreens.kt`: `screenFor(destination)`
  (the route-key → `Screen` table, `error()`s on unknown), the three `Tab`s and
  their `TabNavigatorContent` (each owns a `Navigator`, rebinds the event sink,
  routes `DeviceBack`), the tab-root and "deep" `Screen` objects, parameterized
  `Screen`s with distinct keys (`GameDetail:$id`, etc.), and the `TablessScreen`
  marker (NowPlaying).

- **Composition locals / state holders** — `ActiveTabNavigator.kt`
  (`ActiveTabNavigator` + `TabRouter`, exposing the active tab's `Navigator` /
  the `TabNavigator` to the shell back handler), `AppActionSink.kt`
  (`LocalAppActionSink`), `AppSnackbarHostState.kt` (`LocalAppSnackbarHostState`),
  `ActiveTabDestinations.kt` (`LocalActiveTabDestinations` + `LocalNavigationObserver`
  — programmatic-navigation / observation seams for UI tests + future deep-links),
  `PlatformBackKeys.kt` (`LocalPlatformBackKeys`).

- **Platform expect/actual** — `BackMouseButton.kt` (`Modifier.backMouseButton`;
  JVM/JS watch the pointer back-button, Android is a passthrough since it arrives
  as `KEYCODE_BACK`) and `PerScreenViewModelStore.kt`
  (`WithPerScreenViewModelStore(screen, content)`; Android passthrough,
  JVM/JS hang a `ViewModelStore` off a Voyager `ScreenModel` so each `Screen`
  keeps its own VM across navigation). Actuals live in `androidMain` / `jvmMain` /
  `jsMain`.

- **Tests** — `commonTest/ChipboxAppUiViewModelTest.kt` covers the VM's flows
  and `handleEvent`. `jsTest` is **disabled** (see the long note in
  `build.gradle.kts`): the top-level `staticCompositionLocalOf` declarations drag
  in Skiko, whose JS distribution has no Node loader; the VM is covered on JVM.

## Why depend on this module

The two host apps (`apps/android`, `apps/jvm`) depend on this module and call
`ChipboxAppUi(...)`. Beyond the composable, this module is the **Metro aggregation
hub**: it pulls in every feature's `:real` module as `api()` (not
`implementation()`) so each feature's `@ContributesIntoMap` ViewModel binding is
re-exposed to the app graph's FIR pass — without `api()`, Metro doesn't see the
hints in the app module and VMs crash at runtime with `Unknown model class`.
`api()` on the feature `:real`s also transitively exposes their `:api` route-key
markers that `ChipboxScreens` references. Adding a new screen means adding a
`when` arm to `screenFor(...)` here and wiring its `:real` as `api()`.

## Using it

```kotlin
// Android MainActivity (and JVM DesktopMain) call the same composable:
setContent {
    ChipboxAppUi(
        onOpenUrl = { url -> startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
        onCopyToClipboard = { label, text ->
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        },
        // Optional: window/activity back keys, programmatic nav, a nav observer.
        backKeyEvents = null,
        activeTabDestinations = null,
        onNavigate = null,
    )
}
```

Registering a new screen is a one-line `when` arm in `screenFor(...)`:

```kotlin
internal fun screenFor(destination: Any): Screen = when (destination) {
    Home -> HomeDeepScreen
    is GameDetail -> GameDetailDeepScreen(destination.id) // parameterized → distinct key
    // …
    else -> error("No Voyager Screen registered for destination $destination")
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `sage.compose.kmp` + `metro` +
  `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js` (`jsTest`
  disabled — Skiko/Node, see `build.gradle.kts`)
- **Source set:** `commonMain` (most code) + `androidMain` / `jvmMain` / `jsMain`
  (`backMouseButton` + `WithPerScreenViewModelStore` actuals) + `commonTest`
- **SAGE/module dependencies:** every feature `:real` wired as `api()` (home,
  library, search, settings, nowplaying, browse-*, favorites, playlists,
  game/artist-detail, manage-library, folder-picker, rescan-status, and the
  debug-gated playback-status / error-log / crash-log / component-library);
  `api(:cbox:common:player-status:api)`; `:cbox:common:ui:chrome:api`,
  `:cbox:common:ui:components:api`, `:cbox:common:ui:list:api`,
  `:cbox:common:ui:theme:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`,
  `:cbox:common:settings:api`, `:cbox:common:debug:api`, `:cbox:common:models:api`;
  `sage.common.di`, `sage.common.appinfo`, `sage.common.ui.listScreens`,
  `sage.common.ui.iconsReal`; Voyager (navigator / tab-navigator / transitions /
  screenmodel), Metro-X viewmodel, JetBrains Compose adaptive navigation-suite.
  Test: `:cbox:common:settings:fake`, `:cbox:common:debug:fake`.
