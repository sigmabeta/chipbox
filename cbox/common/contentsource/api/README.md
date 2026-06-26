# `:cbox:common:contentsource:api`

> The content-source contract — where library files come from and how to read
> their bytes.

The `:api` module that abstracts the library's file backing. A `ContentSource`
opens bytes by identifier; a `LibrarySource` extends it with the user's
locations and a walk over them. Pure platform-neutral contracts — both the
scanner and the player resolve files through these, so neither knows whether the
bytes came from the local filesystem (Android or JVM, via the `file/real` impl)
or anywhere else.

## Contents

| File | What it is |
| --- | --- |
| `ContentSource.kt` | Base interface: a `sourceId` and `suspend openBytes(identifier): ByteArray?`. |
| `LibrarySource.kt` | A `ContentSource` that also exposes a `locations` `StateFlow`, a `scanFolders()` walk, and add/remove-location. |
| `LibraryFileInfo.kt` | One discovered file: `identifier`, `parentFolderId` (the scanner's grouping key), name/ext/mime, size, and last-modified ms (size+mtime feed the unchanged-folder signature). |
| `LibraryFolderInfo.kt` | One discovered folder + all its direct files. `scanFolders()` streams these so the scanner can read each folder (its complete file set) while later folders are still being discovered. |
| `LibraryLocationInfo.kt` | One user-added location: `identifier` (same shape as a file id) + optional display name. |
| `ContentSourceRegistry.kt` | Indexes a `Set<ContentSource>` by `sourceId`; `get(sourceId)` resolves the source that owns a track. |

## Why depend on this module

Depend on `:api` for the `LibrarySource` / `ContentSource` types — the scanner
walks a `LibrarySource`, the player resolves track bytes through a
`ContentSource`, and DI builds a `ContentSourceRegistry` from the bound sources.
The production filesystem impl lives in `:cbox:common:contentsource:file:real`;
`:cbox:common:contentsource:fake` supplies an in-memory `LibrarySource` for tests.

## Using it

```kotlin
class SomeReader(private val source: ContentSource) {
    suspend fun load(file: LibraryFileInfo): ByteArray? = source.openBytes(file.identifier)
}

// Resolve the right source for a track by its stamped source id:
val source = ContentSourceRegistry(boundSources).get(track.source)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `sage.common.coroutines` (leaf otherwise — no project deps)
