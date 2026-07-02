# `:cbox:common:organizer:api`

> The plan/result types for "Organize Library".

An `:api` module: just the multiplatform types the library organizer produces and
returns, with no logic. Splitting these out lets callers (and tests) reference the
shapes without pulling in the planner/executor in `:cbox:common:organizer:real`.

## Contents

| File | What it is |
| --- | --- |
| `FolderMove.kt` | `data class FolderMove` — one folder's worth of files to relocate (`category`, `folderName`, `source`/`destination` as okio `Path`, the `entries` to move, and `sourceIsRoot`). Uses `okio.Path`, not `java.io.File`, so it stays multiplatform. |
| `OrganizeResult.kt` | `data class OrganizeResult(movedFolders, failedFolders)` plus the `INVALID_CATEGORY = "Invalid Folders"` bucket name for games split across multiple folders. |

## Why depend on this module

Depend on `:cbox:common:organizer:api` for the move-plan/result types — e.g. UI or
a caller that displays or persists an organize plan without needing to run it. The
production planner/executor lives in `:cbox:common:organizer:real`, which
`api`-exposes this module, so depending on `:real` gives you these types too.

## Using it

These are pure data types; you receive them from `LibraryOrganizer` (in `:real`):

```kotlin
val moves: List<FolderMove> = organizer.plan(games, destination, libraryLocations)
val result: OrganizeResult = organizer.commit(moves)
// result.movedFolders / result.failedFolders
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `api` `okio` (for `Path`); otherwise a leaf
