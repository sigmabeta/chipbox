# `:features:browse-by-platform:real`

> The "browse by platform" Library screen — a single-column list of the platforms
> in your library.

The `:real` implementation of the `browse-by-platform` feature: a
`ChipboxListViewModel`, its state/action types, and the Compose route. It renders
the available platforms as a one-column list and navigates to the games for a
platform on tap. Unlike the other browse screens it is **not** paginated — it just
collects `Repository.getAvailablePlatforms()`.

## Contents

| File | What it is |
| --- | --- |
| `BrowseByPlatformAction.kt` | `sealed BrowseByPlatformAction : ChipboxAction()`; its only case is `PlatformClicked(platform: Platform)` (note the arg is the `Platform` enum). |
| `BrowseByPlatformState.kt` | `BrowseByPlatformState : ListState()`, `columnType = One`. Holds `LCE<List<Platform>>`; emits `IconNameListModel` rows sorted by `Platform.ordinal`, each with a per-medium icon from a private `Platform.icon()` (disc for CD consoles, chip for cartridge/board systems, computer for PC); shows an empty state when there are no platforms. |
| `BrowseByPlatformViewModel.kt` | `@Inject @ContributesIntoMap(AppScope, binding<ViewModel>()) @ViewModelKey` VM extending `ChipboxListViewModel<BrowseByPlatformState>`. Ctor takes `Repository` + `StringProvider` + `Hatchet` (no route args, no `Director`, no pagination). Collects `getAvailablePlatforms()`; `handleAction` navigates to `GamesForPlatform` on tap. |
| `BrowseByPlatformRoute.kt` | `@Composable BrowseByPlatformRoute(onEvent, modifier)` — resolves the VM via `metroViewModel()` and hands it to `ChipboxListEntry`. |
| `BrowseByPlatformViewModelTest.kt` | `commonTest` — maps each `Data` case to the right `LCE`, and asserts `PlatformClicked` emits `NavigateTo(GamesForPlatform(...))`. |

## Why depend on this module

The app depends on `:features:browse-by-platform:real` to actually show the screen;
it's registered in `ChipboxScreens.kt` as the parameterless `BrowseByPlatformScreen`
and the VM is contributed (plain, non-assisted) into the `AppScope` map. Code that
only needs to *navigate* here should depend on `:api` instead. Note that `:real`
depends on `:features:games-for-platform:api` so it can emit that route key on tap.

## Using it

```kotlin
BrowseByPlatformRoute(onEvent = onEvent)
```

Tapping a platform row emits `NavigateTo(GamesForPlatform(platform))`.

## Module facts

- **Plugin:** `chipbox.plugins.feature.real` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (+ `commonTest`)
- **SAGE/module dependencies:** `api` `:features:browse-by-platform:api`;
  `implementation` `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`,
  `:cbox:common:strings:api`, `:cbox:common:repository:api`, `:cbox:common:models:api`,
  `:features:games-for-platform:api`; `commonTest` `:cbox:common:repository:fake`
