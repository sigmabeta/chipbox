# `:cbox:common:organizer:real`

> `LibraryOrganizer` — plans and executes the `$destination/$platform/$game/` library layout.

The production implementation over `:cbox:common:organizer:api`. `LibraryOrganizer`
turns the scanned games into folder moves and applies them on disk, reorganizing
a library into a `$destination/$platform/$game/` structure. It walks and moves
files through an injected okio `FileSystem` (no `java.io`) and takes a
`StringProvider` to resolve per-platform folder names, so the logic is fully
multiplatform and usable by any app (Android / JVM / CLI).

## Contents

| File | What it is |
| --- | --- |
| `LibraryOrganizer.kt` | `class LibraryOrganizer(fileSystem, strings)`. `plan(games, destination, libraryLocations)` builds a `List<FolderMove>`; `commit(moves)` performs them and returns an `OrganizeResult`. A game whose tracks span more than one folder is "invalid" and each folder routes to `$destination/Invalid Folders/$game-$index/`. Handles name sanitization/de-duplication and cross-filesystem moves (atomic rename, falling back to copy-then-delete). |
| `LibraryOrganizerTest.kt` | Covers `plan`/`commit` over an okio `FakeFileSystem`. |

## Why depend on this module

Depend on `:cbox:common:organizer:real` to actually run an organize pass; depend on
`:cbox:common:organizer:api` alone if you only need the `FolderMove`/`OrganizeResult`
types (this module `api`-exposes that one). There is no `:organizer:di` in this set,
so `LibraryOrganizer` is constructed directly — wired by hand or by the depending
module's own DI graph with a caller-supplied okio `FileSystem` and `StringProvider`,
keeping it free of any single app's wiring.

## Using it

```kotlin
val organizer = LibraryOrganizer(fileSystem, stringProvider)

val moves = organizer.plan(
    games = scannedGames,
    destination = "/storage/Music/Chipbox".toPath(),
    libraryLocations = setOf("/storage/Music/rips"),
)
val result = organizer.commit(moves)
// result.movedFolders, result.failedFolders
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `api` `:cbox:common:organizer:api`,
  `:cbox:common:models:api` (`Game`/`Track`/`Platform`); `implementation` `okio`,
  `sage.common.ui.strings` (`StringProvider`). Tests use `okio.fakefilesystem`.
