# `:features:manage-library:real`

> The manage-library screen: list your library folders, add one (kicks off a scan), tap to remove.

The `:real` implementation of the manage-library feature. It contributes the
`ManageLibraryViewModel` to the app's Metro graph, renders its state through the
shared list UI, and exposes the Compose `ManageLibraryRoute` entry point. The
app includes this module to actually show the screen; everyone else navigates to
it via `:features:manage-library:api`.

The screen shows an "Add folder" CTA pinned to the top followed by the current
library folders (or an empty state). Adding a folder commits it to
`LibrarySource`, starts a scan, and navigates to the rescan-status screen;
tapping a folder row removes it from the library.

## Contents

**State / actions** (`commonMain`)
- `ManageLibraryState.kt` — `ManageLibraryState(folders)` extends `ListState`;
  `toListItems(...)` builds a `CtaListModel` ("Add folder") plus one
  `NameCaptionListModel` per folder, or an `EmptyStateListModel` when empty. Also
  defines the `LibraryFolder(identifier, displayName)` row model.
- `ManageLibraryAction.kt` — `ManageLibraryAction` (`ChipboxAction`):
  `AddFolderClicked`, `FolderPicked(uri)`, `FolderClicked(identifier)`.

**ViewModel** (`commonMain`)
- `ManageLibraryViewModel.kt` — `ChipboxListViewModel<ManageLibraryState>`,
  `@ContributesIntoMap(AppScope)` + `@ViewModelKey` + `@Inject`. `init` mirrors
  `LibrarySource.locations` into `state.folders`; `handleAction` maps
  `AddFolderClicked → ChipboxEvent.PickFolder`, `FolderPicked` → add location +
  `scanner.startScan()` + navigate to `RescanStatus`, `FolderClicked` → remove
  location + confirmation snackbar.

**Route** — `expect`/`actual` Compose `ManageLibraryRoute(onEvent, modifier)`
- `commonMain/ManageLibraryRoute.kt` — the `expect` declaration. It's
  platform-specific because the folder picker behind `ChipboxEvent.PickFolder`
  differs per target.
- `androidMain/ManageLibraryRoute.kt` — intercepts `PickFolder` and pushes the
  in-app `:features:folder-picker` screen (Android dropped SAF
  `OpenDocumentTree` for raw-path libraries + All Files Access; the picker
  enforces the storage permission and commits the path itself).
- `jvmMain/ManageLibraryRoute.kt` — also pushes the in-app `:features:folder-picker`
  screen instead of a Swing `JFileChooser`; the picker commits the path and
  starts the scan itself.
- `jsMain/ManageLibraryRoute.kt` — enforcement-only purity stub: no folder
  picker, just wires the view model + `ChipboxListEntry` and forwards every
  event unchanged.

All actuals resolve the VM via `metroViewModel()` and render it through
`ChipboxListEntry`; the Android/JVM actuals route `PickFolder` locally and
forward every other event to the host.

**Tests** (`commonTest`)
- `ManageLibraryViewModelTest.kt` — covers the three actions and the
  `LibrarySource.locations` collector, using `FakeLibrarySource` and
  `CountingScanner`.

## Why depend on this module

The app (and the screenshot companion) depend on `:real` to render and wire the
screen. To *navigate* here, depend on `:api` for the `ManageLibrary` key
instead; only the app graph needs `:real` so the `ManageLibraryViewModel`
binding is contributed.

## Using it

```kotlin
// In ChipboxScreens.kt the route key maps to a Voyager Screen that calls:
ManageLibraryRoute(onEvent = LocalChipboxEventSink.current)

// The actual resolves the VM from the Metro graph and renders the list:
val viewModel: ManageLibraryViewModel = metroViewModel()
ChipboxListEntry(viewModel, routedOnEvent, modifier)
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.real` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js` (the
  `feature.real` plugin transitively applies `sage.kmp.js`)
- **Source set:** `commonMain` (state/action/VM + `expect` route) with
  `androidMain` / `jvmMain` / `jsMain` actuals for `ManageLibraryRoute`; tests in
  `commonTest`
- **SAGE/module dependencies:** `:features:manage-library:api`,
  `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`,
  `:cbox:common:strings:api`, `:cbox:common:contentsource:api`,
  `:cbox:common:scanner:api`, `:features:rescan-status:api`; `:features:folder-picker:api`
  (androidMain + jvmMain); test: `:cbox:common:contentsource:fake`,
  `:cbox:common:scanner:fake`
