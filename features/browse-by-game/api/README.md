# `:features:browse-by-game:api`

> Route key for the Browse-by-Game screen.

The public contract of the browse-by-game feature: a single Voyager route key
that other modules navigate to. As the `:api` half of the api/real split, it
carries the route key and the screen's State/Action types — the screen implementation lives in `:real` — so callers
can target the destination without depending on the (much heavier) screen code.

## Contents

| File | What it is |
| --- | --- |
| `BrowseByGame.kt` | `@Serializable data object BrowseByGame` — the parameterless route key for the screen. |
| `BrowseByGameAction.kt` | `sealed BrowseByGameAction : ChipboxAction()` with one case, `GameClicked(id: Long)` — the only user action. |
| `BrowseByGameState.kt` | `BrowseByGameState : ListState()`. A 160dp `ColumnType.Regular` cover grid with `PaginationType.Standard()`. Holds `LCE<List<Game>>` plus a sliding paging window (`windowStart`, `hasMoreBefore`/`hasMoreAfter`, `loadingPrevious`/`loadingMore`); renders inline header/footer paging spinners and `GridImageListModel` covers at 3:4 aspect ratio, with an empty state when loaded-empty. |

## Why depend on this module

Depend on `:features:browse-by-game:api` when you need to navigate to the
browse-by-game screen — e.g. the Library tab references `BrowseByGame` as a
navigation destination. It carries no ViewModel or Compose dependencies; the
state/action dependencies it does expose are declared in its build file. The app wires the
actual screen via `:real` (registered in `ChipboxScreens.kt`); you only need
`:api` to point at it.

## Using it

```kotlin
// Navigate to the screen by emitting its route key.
onEvent(NavigateTo(BrowseByGame))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** the public dependencies of the State/Action types, declared in `build.gradle.kts`; `kotlinx.serialization` comes from the convention plugin
