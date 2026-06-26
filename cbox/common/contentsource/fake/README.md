# `:cbox:common:contentsource:fake`

> An in-memory `LibrarySource` for tests — drives the `locations` flow and
> records add/remove calls.

The `:fake` test double for `:cbox:common:contentsource:api`. `FakeLibrarySource`
backs `locations` with a mutable in-memory list so view-model tests can observe
and push location changes without touching the filesystem.

## Contents

| File | What it is |
| --- | --- |
| `FakeLibrarySource.kt` | `LibrarySource` over a `MutableStateFlow` of locations. `setLocations` pushes values; `addLibraryLocation`/`removeLibraryLocation` mutate the list and record into `addedLocations`/`removedLocations`. `openBytes` returns null and `scanFolders` an empty flow (unused by VM tests). |

## Why depend on this module

Use it in tests for any view-model that observes `locations` or dispatches
add/remove (e.g. the library-locations settings screen). The recording lists let
assertions verify the VM called the right thing; `setLocations` lets the test
drive the VM's init-time collect.

## Using it

```kotlin
val source = FakeLibrarySource(initial = listOf(LibraryLocationInfo("/music", "music")))
val vm = LibrarySettingsViewModel(source)

vm.removeLocation("/music")
assertEquals(listOf("/music"), source.removedLocations)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:contentsource:api`, `kotlinx-coroutines-core`
