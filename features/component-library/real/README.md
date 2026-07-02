# `:features:component-library:real`

> The debug-only component gallery screen: every reusable `ListModel` rendered in each layout mode.

The `:real` half of the `component-library` feature: the ViewModels, states,
Route composables, and sample-content generator that implement the gallery whose
route keys live in `:features:component-library:api`. `:real` modules hold the
screen implementation (`ChipboxListViewModel`s wired into the Metro `AppScope`
graph, Compose content); the app includes this module. There is no `:screenshot`
companion for this feature.

The gallery is a developer aid: it renders one of (most) every SAGE/Chipbox
`ListModel` so they can be eyeballed under each list layout. Reached from the
Settings debug section (gated behind `shouldShowDebug`).

## Contents

- **ViewModels** — `ComponentLibraryViewModel` (`@ContributesIntoMap`+`@ViewModelKey`+`@Inject`) drives the landing menu and emits `NavigateTo(ComponentLibraryMode(...))` when a row is tapped; `ComponentLibraryModeViewModel` (`@AssistedInject` + `Factory`) takes the chosen `LibraryMode`, seeds a stable sample set in `init`, and handles the one interactive control (the dropdown).
- **States** — `ComponentLibraryState` builds the menu (one `IconNameListModel` per `LibraryMode`); `ComponentLibraryModeState` maps the mode to a `ColumnType` (`One`/`Regular`/`Staggered`) and emits the showcase rows (`listItems`/`gridItems`/`columnItems`).
- **Action** — `ComponentLibraryAction` (`OpenMode`, `DropdownExpandClicked`); sample rows otherwise use `SageAction.Noop`.
- **Routes** — `ComponentLibraryRoute` (menu) and `ComponentLibraryModeRoute`; the latter forces `LocalInspectionMode = true` so the synthetic `sourceInfo` strings render deterministic `BitmapGenerator` gradients instead of failing image loads.
- **Sample content** (`expect`/`actual`) — `SampleContent` + `generateSampleContent`; the JVM/Android `actual` (`src/main/java`) uses SAGE's `StringGenerator`, the JS `actual` returns deterministic placeholders.

## Why depend on this module

Only the app's navigation wiring (`ChipboxScreens.kt`) depends on `:real`, to map
the `ComponentLibrary`/`ComponentLibraryMode` route keys to Voyager `Screen`s.
Everything else depends on `:api` for the route keys. The ViewModels register
themselves into the Metro graph via `@ContributesIntoMap`, so no `:di` module is
needed.

## Using it

```kotlin
// In ChipboxScreens.kt — the route keys map to Screens that wrap these Routes:
ComponentLibrary -> ComponentLibraryMenuScreen          // ComponentLibraryRoute(...)
is ComponentLibraryMode -> ComponentLibraryModeScreen(destination.mode)  // ComponentLibraryModeRoute(mode, ...)
```

## Module facts

- **Plugin:** `chipbox.feature.real`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (ViewModels, states, routes); `src/main/java` (`jvmSharedMain`) for the `StringGenerator`-backed sample `actual`; `jsMain` for the placeholder `actual`
- **SAGE/module dependencies:** `:features:component-library:api`; `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`; `sage.common.images`, `sage.common.ui.components`, `sage.common.ui.strings` (jvmShared), `kotlinx.collections.immutable`
