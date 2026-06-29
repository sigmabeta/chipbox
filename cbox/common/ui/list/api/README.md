# `:cbox:common:ui:list:api`

> The list-screen integration layer: the base `ChipboxListViewModel` every feature extends, plus the Compose entry points that bind it to SAGE's list/grid screens.

The `:api` module at the heart of Chipbox's feature-screen pattern. It supplies
the abstract `ChipboxListViewModel` that every list-based feature subclasses and
the shared Compose scaffolding (`ChipboxListEntry`, `ChipboxReorderableEntry`)
that wires a VM's rendered state into SAGE's `ListScreen`/`GridScreen`/
`ReorderableScreen`. Being `:api`, it is the public contract feature `:api`/`:real`
modules build against; there is no `:real` impl — the building blocks live here.

Read `arch-docs/architecture/feature-screens.md` for the end-to-end anatomy of a
feature screen; this module provides the VM base and the Route's rendering half.

## Contents

| File | What it is |
| --- | --- |
| `ChipboxListViewModel.kt` | The base VM class every list feature extends. `abstract class ChipboxListViewModel<S : ListState> : ChipboxViewModel(), ActionSink`. Holds immutable typed `state: StateFlow<S>`, derives `uiStateActual: StateFlow<ListStateActual>` via `S.toActual(stringProvider)` (eagerly started), exposes `events: SharedFlow<ChipboxEvent>` (one-shot effects) and `showDebug`. Subclasses call `protected updateState { it.copy(...) }` and `protected emit(event)`; `final sendAction()` logs each action then dispatches to the `abstract handleAction(SageAction)`. The deliberately-thin replacement for SAGE's `ListViewModelBrain`. |
| `ChipboxListEntry.kt` | `@Composable ChipboxListEntry(viewModel, onEvent, modifier)` — the rendering half of a Route. Forwards `events` to `onEvent`, runs `ScreenLifecycleEffect`, sets the title bar, then `BoxWithConstraints` measures width → `WidthClass` (COMPACT/MEDIUM/EXPANDED at 600/840 dp breakpoints) → column count via `state.columnType.numberOfColumns(...)`. Renders `GridScreen` (>1 column; honors `ColumnType.Staggered`) or `ListScreen` (1 column), passing the VM itself as the `ActionSink`. |
| `ChipboxReorderableEntry.kt` | Drag-reorder sibling of `ChipboxListEntry` (single column, no grid). Same event/lifecycle/title plumbing, but renders SAGE's `ReorderableScreen`. Its `itemContent` peels wrappers outside-in: `DismissibleListModel` → `SwipeToRemoveBox`, `DraggableListModel` → `DraggableListItem` with a drag handle, else a plain row. Reorder needs no wiring here — `ReorderableScreen` emits `SageAction.Reorder` to the VM's `handleAction` on drop. |
| `ScreenLifecycleEffect.kt` | Centralized screen-lifecycle → active-VM dispatch. `ScreenLifecycleEffect(actionSink)` reads `LocalScreenLifecycleOwner` (a `staticCompositionLocalOf<LifecycleOwner?>` defaulting to `null`) and, via `LifecycleResumeEffect`, sends `SageAction.Resume` on ON_RESUME and `SageAction.Pause` on ON_PAUSE/dispose. No-ops outside the app shell (e.g. previews / isolated Route tests), so they don't crash on an absent lifecycle owner. |

## Why depend on this module

Every `features/*/real` module depends on this to write its ViewModel and Route:
extend `ChipboxListViewModel<MyState>` and hand it to `ChipboxListEntry` (or
`ChipboxReorderableEntry` for drag-to-reorder screens). Feature `:api` modules
that only declare `State`/`Action`/route keys typically don't need it — they
depend on SAGE's `:list` for `ListState` directly. The app host
(`ChipboxAppUi`) provides `LocalScreenLifecycleOwner` so foreground/background
reaches the active screen's VM.

## Using it

```kotlin
// :real — the ViewModel
class LibraryViewModel @Inject constructor(
    private val repository: Repository,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<LibraryState>(LibraryState(), stringProvider, hatchet) {

    override fun handleAction(action: SageAction) {
        when (action) {
            SageAction.Resume -> viewModelScope.launch { /* refresh */ }
            is LibraryAction.ItemClicked ->
                emit(ChipboxEvent.NavigateTo(GameDetail(action.id)))
            else -> Unit
        }
    }

    private fun onLoaded(games: List<Game>) = updateState { it.copy(games = games) }
}

// :real — the Route
@Composable
fun LibraryRoute(onEvent: (ChipboxEvent) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LibraryViewModel = metroViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `sage.compose.kmp`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** SAGE `common.list`, `common.appcomm`, `common.ui.strings`, `common.ui.components`, `common.logging`, `common.ui.listScreens` (impl); `androidx.lifecycle.viewmodel` + `runtimeCompose`. Project: `:cbox:common:appcomm:api`, `:cbox:common:ui:vm:api` (api), `:cbox:common:ui:chrome:api`, `:cbox:common:ui:components:api` (impl).
