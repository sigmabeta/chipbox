# `:features:folder-picker:real`

> The in-app folder browser screen — ViewModel, per-OS `expect`/`actual` Route, and an Okio-backed directory lister.

The `:real` half of the folder-picker feature screen: the implementation the app
includes to actually show the picker. It bundles the `ChipboxListViewModel`, the
`State`/`Action`/`Entry` value types, the platform-specific Compose `Route`, and a
`FolderLister` filesystem abstraction with an Okio-backed implementation. This is
the desktop/in-app folder browser that `SettingsRoute` (and `ManageLibraryRoute`)
push on JVM — instead of a system file dialog, the user descends through real
filesystem paths in Chipbox's own list UI, then "Add this folder" commits the
directory and kicks off a library scan.

## Contents

**State / Action types** (`commonMain`)
- `FolderPickerState.kt` — `FolderPickerState : ListState`, the pure renderer: pins
  the "Add this folder" / "Go up a folder" / show-hidden toggle / "Cancel" CTAs to
  the top, renders one `LabelValueListModel` row per subfolder ("N folders, M
  files") and a single aggregate `SingleTextListModel` files row. Title shows the
  current path, middle-ellipsized past 64 chars. Also defines `FolderPickerEntry`
  (one subfolder row: name, native path, child folder/file counts).
- `FolderPickerAction.kt` — `FolderPickerAction : ChipboxAction`: `AddThisFolderClicked`,
  `CancelClicked`, `NavigateUpClicked`, `ToggleHiddenClicked`, `FolderClicked(path)`.

**ViewModel** (`commonMain`)
- `FolderPickerViewModel.kt` — `@AssistedInject` VM taking the per-OS `defaultPath`
  as an assisted arg (via `ManualViewModelAssistedFactory`, `@ContributesIntoMap(AppScope)`).
  Lists the default directory on init, handles descend/ascend/toggle-hidden by
  re-listing in place, and on "Add" calls `LibrarySource.addLibraryLocation(path)` +
  `Scanner.startScan()` then navigates to the `RescanStatus` screen.

**FolderLister** (`commonMain`)
- `FolderLister.kt` — the `FolderLister` interface (`list(path, showHidden) → FolderListing`)
  plus the `FolderListing` result type (subfolders, rolled-up file count, parent path).
  Abstracted so the VM stays testable off-disk.
- `OkioFolderLister.kt` — `@ContributesBinding(AppScope)` Okio-backed impl. Lists a
  directory via an injected `okio.FileSystem`, sorts subfolders by lower-cased name,
  peeks one level into each for child counts, filters dotfiles unless `showHidden`,
  and collapses unreadable directories to an empty listing (still reporting the parent
  so the user can ascend out).

**Route** (`expect` in `commonMain`, actuals per target)
- `commonMain/FolderPickerRoute.kt` — the `expect fun FolderPickerRoute(onEvent, modifier)`.
  `expect`/`actual` only because the *initial directory* differs per OS; every actual
  forwards `onEvent` untouched (no platform events to intercept).
- `androidMain/FolderPickerRoute.kt` — defaults to `Environment.getExternalStorageDirectory()`,
  but gates the picker behind All Files Access (`MANAGE_EXTERNAL_STORAGE`, API 30+) or
  legacy `READ_EXTERNAL_STORAGE` (API <30), showing a `StoragePermissionPrompt` that
  launches the grant flow until access is held.
- `jvmMain/FolderPickerRoute.kt` — defaults to `System.getProperty("user.home")` (falling
  back to `/`).
- `jsMain/FolderPickerRoute.kt` — enforcement-only stub (`= Unit`) to keep the `expect`
  satisfied; no DI graph or filesystem on JS.

**Tests** (`commonTest`) — `FolderPickerViewModelTest.kt` and `OkioFolderListerTest.kt`
(the latter exercises the Okio code paths against a `FakeFileSystem`).

> Note: the `FolderLister` KDoc still references an older `JvmFolderLister`/`jvmSharedMain`
> binding; the production binding is `OkioFolderLister` in `commonMain`.

## Why depend on this module

The app (`apps/android`, `apps/jvm`) depends on `:real` so the `FolderPicker` route
key resolves to a live screen — `ChipboxScreens.kt` renders the `expect`
`FolderPickerRoute`, and Metro contributes both the `FolderPickerViewModel` factory
and the `OkioFolderLister` → `FolderLister` binding into `AppScope`. Other *features*
should depend on `:api` (the route key) to navigate here, not on `:real`. The
`FileSystem.SYSTEM` binding `OkioFolderLister` needs is provided by each app's DI
module, not by this module.

## Using it

The screen is reached via the route key, then rendered by appui through the
`expect`/`actual` Route:

```kotlin
// in a ViewModel: navigate to the picker
emit(ChipboxEvent.NavigateTo(FolderPicker))

// appui renders the platform actual, which resolves the assisted VM with the
// per-OS default directory:
@Composable
actual fun FolderPickerRoute(onEvent: (ChipboxEvent) -> Unit, modifier: Modifier) {
    val defaultPath = remember { System.getProperty("user.home") ?: "/" } // jvmMain actual
    val viewModel = assistedMetroViewModel<FolderPickerViewModel, FolderPickerViewModel.Factory> {
        create(defaultPath)
    }
    ChipboxListEntry(viewModel, onEvent, modifier)
}
```

`OkioFolderLister` is injected, not constructed by callers — Metro binds it from
`FileSystem.SYSTEM`.

## Module facts

- **Plugin:** `chipbox.feature.real` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js` (the `feature.real` plugin transitively applies `sage.kmp.js`)
- **Source set:** `commonMain` (State/Action/VM/`FolderLister`/`OkioFolderLister`) + `androidMain`/`jvmMain`/`jsMain` Route actuals; tests in `commonTest`. No `jvmSharedMain` code.
- **SAGE/module dependencies:** `:features:folder-picker:api`, `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`, `:cbox:common:contentsource:api`, `:cbox:common:scanner:api`, `:features:rescan-status:api`, `okio` (commonMain); `androidx.activity.compose` + `:cbox:common:ui:components:api` (androidMain); fakes (`contentsource.fake`, `scanner.fake`, `okio.fakefilesystem`) for tests.
