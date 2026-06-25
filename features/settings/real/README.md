# `:features:settings:real`

> The settings screen's ViewModel and platform Route — persistence wiring plus the per-platform folder picker.

The `:real` half of the `settings` feature: `SettingsViewModel` and the
`SettingsRoute` composable that implement the screen whose route key, actions, and
state-renderer live in `:features:settings:api`. `:real` modules hold the screen
implementation (a `ChipboxListViewModel` wired into the Metro `AppScope` graph,
Compose content); the app includes this module. There is no `:screenshot`
companion for this feature.

The ViewModel observes a dozen settings/debug flows and pushes their values into
`SettingsState`, persists every selection back through the managers, runs the
clear-library / clear-history operations (with `LCE` loading state), starts
library scans, and translates navigation actions into `ChipboxEvent.NavigateTo`
(manage library, rescan status, playback status, error log, crash log, component
library). It also implements the hidden five-tap-the-build-date toggle that
reveals the debug section.

## Contents

- **ViewModel** — `SettingsViewModel` (`@ContributesIntoMap`+`@ViewModelKey`+`@Inject`): collects `ChipboxSettingsManager` / `DebugSettingsManager` / `LibrarySource` / `Scanner` flows into state, persists selections, handles clear/rescan/navigation actions and the debug-unlock tap counter.
- **Route** (`expect`/`actual`) — `SettingsRoute` is `expect` in `commonMain`; each `actual` resolves the VM with `metroViewModel()` and intercepts `ChipboxEvent.PickFolder` locally (forwarding every other event to the host sink). `androidMain` and `jvmMain` both push the in-app `:features:folder-picker` screen (Android dropped SAF `OpenDocumentTree` for raw-path libraries + All Files Access); `jsMain` is an enforcement-only stub that forwards events unchanged.
- **Build date** (`expect`/`actual`) — `formatLongDate(epochMs)` for the about-section build timestamp: JVM/Android (`src/main/java`) use `java.time` `FormatStyle.LONG`; the JS `actual` returns the raw epoch string.

## Why depend on this module

Only the app's navigation wiring (`ChipboxScreens.kt`) depends on `:real`, to map
the `Settings` route key to a Voyager `Screen` (calling `SettingsRoute`).
Everything else depends on `:api` for the route key, actions, and state. The VM
registers itself into the Metro graph via `@ContributesIntoMap`, so no `:di`
module is needed. `:real` also pulls in the route keys of the screens settings
navigates to (`playback-status`, `error-log`, `crash-log`, `component-library`,
`manage-library`, `rescan-status`, and `folder-picker` per platform).

## Using it

```kotlin
// In ChipboxScreens.kt:
Settings -> SettingsDeepScreen    // SettingsRoute(onEvent = LocalChipboxEventSink.current)
```

## Module facts

- **Plugin:** `chipbox.feature.real` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (VM + `expect` Route/build-date); `androidMain` / `jvmMain` / `jsMain` Route actuals; `src/main/java` (`jvmSharedMain`) + `jsMain` build-date actuals; `commonTest` for `SettingsViewModelTest`
- **SAGE/module dependencies:** `:features:settings:api`; `:cbox:common:ui:list:api`, `:cbox:common:ui:fonts:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`, `:cbox:common:settings:api`, `:cbox:common:debug:api`, `:cbox:common:repository:api`, `:cbox:common:history:api`, `:cbox:common:scanner:api`, `:cbox:common:contentsource:api`; the `:api` route keys of `:features:playback-status`, `:error-log`, `:crash-log`, `:component-library`, `:manage-library`, `:rescan-status`, and `:folder-picker` (android/jvm); `sage.common.appinfo`, `sage.common.ui.components`, `kotlinx.collections.immutable`
