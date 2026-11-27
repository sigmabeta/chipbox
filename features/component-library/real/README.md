# `:features:component-library:real`

> The debug-only component gallery screen: SAGE's component library hosting Chipbox's own cards.

> The state and the Chipbox cards live in `:features:component-library:api`; this module holds the ViewModel and Route.

The `:real` half of the `component-library` feature. The catalog lives in SAGE
(`:common:ui:component-library`); Chipbox only supplies its cards (`chipboxSampleCards()`) and wires
the screen into the Metro `AppScope` graph. One screen switches list, grid and columns in place
through a dropdown, so there is no menu and no per-mode sub-screen. Reached from the Settings debug
section (gated behind `shouldShowDebug`). There is no `:screenshot` companion for this feature.

## Contents

- **ViewModel** — `ComponentLibraryViewModel` (`@ContributesIntoMap` + `@ViewModelKey` + `@Inject`), a `ChipboxListViewModel<ComponentLibraryState>`. `handleAction` forwards SAGE's `ComponentLibraryAction`s to the pure `config.reduce(action)` and ignores everything else.
- **Route** — `ComponentLibraryRoute` collects `uiStateActual` and renders `ComponentLibraryHost(state.listItems) { ChipboxListEntry(...) }`. The host reads the live style from the catalog's carrier row, forces `LocalInspectionMode` (so synthetic `SourceInfo`s draw preview art) and keeps the app's `contentFor`/`previewImage`.
- **Tests** — `ComponentLibraryViewModelTest` (action changes config, column type follows layout, Chipbox cards present, no duplicate ids).

## Why depend on this module

Only the app's navigation wiring (`ChipboxScreens.kt`) depends on `:real`, to map the
`ComponentLibrary` route key to a Voyager `Screen`. Everything else depends on `:api`.

## Module facts

- **Plugin:** `chipbox.feature.real` (+ `chipbox.kmp.test`)
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js` (the SAGE module has its own JS actual)
- **Source set:** `commonMain` only
- **Dependencies:** `:features:component-library:api`; `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`; `libs.sage.common.ui.component.library`
