# Anatomy of a feature screen

Every user-facing screen lives under `features/<name>/` and is a list-based
screen built on SAGE's `ListState`/`ListModel` system, a `ChipboxListViewModel`,
Metro DI, and Voyager navigation. Learn one feature and you know them all.

## Module split

- `features/<name>/api` (`chipbox.feature.api`, KMP) — exports the route key and
  the screen's `State`/`Action` value types (so other features can reference the
  route key, and so the desktop UI builds the same screen from the same types).
  - Route key: `@Serializable data object Settings` (or `data class GameDetail(val id: Long)`).
  - `<Name>State` (extends `ListState`) and `<Name>Action` (extends `ChipboxAction`).
- `features/<name>/real` (`chipbox.feature.real`, KMP) — the ViewModel and the
  Route composable (the impl).
- `features/<name>/screenshot` (`chipbox.screenshot`, Android) — Paparazzi tests.

(Some features keep `State`/`Action` in `:real` instead of `:api` — `settings`
and `game-detail` put them in `:api`. Follow the neighbouring feature.)

## The ViewModel

Base class: `ChipboxListViewModel<S : ListState>`
(`cbox/common/ui/list/api/src/commonMain/kotlin/.../ChipboxListViewModel.kt`).
It extends `androidx.lifecycle.ViewModel` (multiplatform lifecycle artifact) and
exposes:

- `state: StateFlow<S>` — raw typed state (used by tests).
- `uiStateActual: StateFlow<ListStateActual>` — rendered form, derived via
  `S.toActual(stringProvider)` (calls `title()` + `toListItems()`).
- `events: SharedFlow<ChipboxEvent>` — one-shot effects (navigation, snackbar…).
- `protected updateState { it.copy(...) }`, `protected emit(event)`,
  `final sendAction(action)` → dispatches to your `abstract handleAction(SageAction)`.

Constructor params are always `StringProvider` + `Hatchet` plus your
dependencies. Injection is Metro:

**Simple VM** (no route args):

```kotlin
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class SettingsViewModel @Inject constructor(
    private val settingsManager: ChipboxSettingsManager,
    private val repository: Repository,
    stringProvider: StringProvider,
    private val hatchet: Hatchet,
) : ChipboxListViewModel<SettingsState>(SettingsState(), stringProvider, hatchet) {

    override fun handleAction(action: SageAction) {
        when (action) {
            SettingsAction.AddFolderClicked -> emit(ChipboxEvent.PickFolder)
            SettingsAction.ManageLibraryClicked -> emit(ChipboxEvent.NavigateTo(ManageLibrary))
            else -> Unit
        }
    }
}
```

**Route-arg VM** (`@AssistedInject`) — args travel via the assisted factory, NOT
`SavedStateHandle` (AndroidX nav was removed; Voyager pushes typed `Screen`s):

```kotlin
@AssistedInject
class GameDetailViewModel(
    @Assisted private val gameId: Long,
    private val repository: Repository,
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<GameDetailState>(GameDetailState(), stringProvider, hatchet) {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted gameId: Long): GameDetailViewModel
    }
}
```

Async data is wrapped in `LCE<T>` (Loading/Content/Error/Uninitialized), not
`T?`. `ListState` provides `LCE<T>.sectionWithStandardErrorAndLoading(...)` to
turn an `LCE` into list rows with standard loading skeletons + error rows.

## The State

`<Name>State : ListState` is an immutable data class implementing:

- `title(stringProvider): TitleBarModel`
- `toListItems(stringProvider): List<ListModel>`
- optional `override val columnType: ColumnType` (default `One`; also `Regular`,
  `Staggered` → grid).

`toListItems` builds rows from SAGE/chipbox `ListModel` types:
`SectionHeaderListModel`, `NameCaptionListModel`, `IconNameListModel`,
`DropdownSettingListModel`, `LabelValueListModel`, `CtaListModel`,
`HeroImageListModel`, `HorizontalScrollerListModel`, `LoadingItemListModel`,
`ErrorStateListModel`, `EmptyStateListModel`, `NoopListModel` (filtered out).
SAGE's are in `sage/common/ui/components`; chipbox-specific ones in
`cbox/common/ui/components/api`.

## The Route composable

`<Name>Route(onEvent: (ChipboxEvent) -> Unit, modifier)` resolves the VM with
`metroViewModel<VM>()` and hands it to `ChipboxListEntry`, which collects
`uiStateActual`, measures width → column count, and renders via SAGE's
`ListScreen` (1 col) or `GridScreen` (>1):

```kotlin
@Composable
fun LibraryRoute(onEvent: (ChipboxEvent) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LibraryViewModel = metroViewModel()
    ChipboxListEntry(viewModel, onEvent, modifier)
}
```

A Route may be `expect`/`actual` when it needs platform-specific behaviour.
`SettingsRoute` is the canonical example: it's `expect` in commonMain with
actuals per target that intercept `ChipboxEvent.PickFolder` locally — androidMain
uses SAF (`rememberLauncherForActivityResult(OpenDocumentTree)`), jvmMain pushes
the in-app `FolderPicker` screen, jsMain is an enforcement-only stub — and
forward every other event to the host sink.

Rendering/action flow: `ListModel.Content(...)` calls `actionSink.sendAction(action)`
→ VM `sendAction` logs + dispatches to `handleAction` → `updateState`/`emit`.

## Navigation registration (Voyager)

All wiring is in
`cbox/common/appui/api/src/commonMain/kotlin/.../ChipboxScreens.kt`.

- `screenFor(destination: Any): Screen` maps each feature's route key to a
  Voyager `Screen`. Add a `when` arm here for a new screen; it `error()`s on
  unknown destinations (no silent drops).
- Each `Screen` wraps its `Route` in `ScreenScaffold { … }` (resets `ScreenChrome`,
  applies `WithPerScreenViewModelStore`) and pulls `LocalChipboxEventSink.current`
  as the `onEvent` sink.
- Three root tabs (`Home`, `Library`, `Search`) each own a Voyager `Navigator`,
  so per-tab back stacks survive tab switches. VMs emit `ChipboxEvent.NavigateTo`/
  `NavigateBack` → the tab's local sink push/pops the local Navigator; system
  events (snackbar, openUrl, clipboard, picker) bubble to the app-level sink.
- **Parameterized `Screen`s must give a distinct `key`** (e.g. `GameDetail:$id`),
  and on JVM `WithPerScreenViewModelStore` gives each Screen its own
  `ViewModelStore` — otherwise `metroViewModel<VM>()` returns the same cached
  instance across different args (e.g. `GamesForPlatform(GENESIS)` reusing the
  Dreamcast VM).

## Screenshot module

`features/<name>/screenshot` holds:
- a `@DevicePreviews @Composable` that calls `ListScreenPreview(screenState = <Name>State)`
  (wraps the **state object**, not the VM, to avoid runtime/DI deps);
- a `@RunWith(Parameterized::class)` Paparazzi test snapshotting each
  `INTERESTING_DEVICES` config.

Check with `./gradlew verifyPaparazziDebug`; re-record (overwrites LFS goldens)
with `testDebugUnitTest`.

## New-feature checklist

1. `:api` — `@Serializable` route key + `<Name>State`/`<Name>Action`.
2. `:real` — `<Name>ViewModel` (`@ContributesIntoMap`+`@ViewModelKey`+`@Inject`,
   or `@AssistedInject`+factory for args) and `<Name>Route` (`metroViewModel` →
   `ChipboxListEntry`).
3. Register the route key → `Screen` in `ChipboxScreens.kt`.
4. Add module paths to `settings.gradle.kts`; add `:real` to `apps/android` and
   `apps/jvm` deps.
5. `:screenshot` preview + Paparazzi test (optional but conventional).
