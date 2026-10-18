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
reveals the debug section, and the debug-only **snapshot import**: an inline path
field that reads a `library-snapshot.json` (via the injected `LibrarySource`) and
upsert-merges it through `Repository.importLibrarySnapshot`, reporting the count
in a snackbar.

Host policy comes from `AppCapabilities` (injected). It seeds
`showLibraryAdmin` / `showDebugSection` / `showErrorLog` in the state and is also
enforced at the action seam: on a host that does not manage its library, every
admin action (add/manage/rescan/rescan-status/clear-library/folder-picked) is a
no-op, and when the debug section is hidden the five-tap toggle and the debug-only
actions are inert. The Error Log is not a debug action — it stays reachable from
the Diagnostics row.

## Contents

- **ViewModel** — `SettingsViewModel` (`@ContributesIntoMap`+`@ViewModelKey`+`@Inject`): collects `ChipboxSettingsManager` / `DebugSettingsManager` / `LibrarySource` / `Scanner` flows into state, persists selections, handles clear/rescan/navigation actions and the debug-unlock tap counter. Injects `AppCapabilities` to seed policy flags and to reject admin/debug actions the host does not expose.
- **Route** (`expect`/`actual`) — `SettingsRoute` is `expect` in `commonMain`; each `actual` resolves the VM with `metroViewModel()` and intercepts `ChipboxEvent.PickFolder` locally (forwarding every other event to the host sink). `androidMain` and `jvmMain` both push the in-app `:features:folder-picker` screen (Android dropped SAF `OpenDocumentTree` for raw-path libraries + All Files Access); `jsMain` forwards events to the shared sink, whose capability guard shows a "library is managed on the server" snackbar rather than dead-ending.
- **Build date** (`expect`/`actual`) — `formatLongDate(epochMs)` for the about-section build timestamp: JVM/Android (`src/main/java`) use `java.time` `FormatStyle.LONG`; the JS `actual` uses the browser's `Date.toLocaleDateString()` (web now shows a real git build time).

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
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (VM + `expect` Route/build-date); `androidMain` / `jvmMain` / `jsMain` Route actuals; `src/main/java` (`jvmSharedMain`) + `jsMain` build-date actuals; `commonTest` for `SettingsViewModelTest`
- **SAGE/module dependencies:** `:features:settings:api`; `:cbox:common:ui:list:api`, `:cbox:common:ui:fonts:api`, `:cbox:common:appcomm:api`, `:cbox:common:capabilities:api`, `:cbox:common:strings:api`, `:cbox:common:settings:api`, `:cbox:common:debug:api`, `:cbox:common:repository:api`, `:cbox:common:history:api`, `:cbox:common:scanner:api`, `:cbox:common:contentsource:api` (the `LibrarySnapshot` DTO, `LibrarySnapshotJson`, and the `importLibrarySnapshot` capability all come from `:cbox:common:repository:api`); the `:api` route keys of `:features:playback-status`, `:error-log`, `:crash-log`, `:component-library`, `:manage-library`, `:rescan-status`, and `:folder-picker` (android/jvm); `sage.common.appinfo`, `sage.common.ui.components`, `kotlinx.collections.immutable`
