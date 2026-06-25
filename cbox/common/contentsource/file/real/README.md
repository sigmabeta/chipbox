# `:cbox:common:contentsource:file:real`

> The local-filesystem `LibrarySource` — identifies files and library roots by
> absolute path.

The `:real` filesystem impl of `:cbox:common:contentsource:api`.
`LocalFileContentSource` is a raw `java.io.File` `LibrarySource` (source id
`"file"`): it walks library roots, opens bytes by absolute path, and persists
the user's locations as a newline-separated text file. It lives in
`jvmSharedMain` (`src/main/java`), so both the JVM/desktop app and the Android
app share it — Android uses `MANAGE_EXTERNAL_STORAGE` + raw paths rather than
SAF, so the two targets drive the same walker (and the same shared `RealScanner`).

## Contents

| File | What it is |
| --- | --- |
| `LocalFileContentSource.kt` | The `LibrarySource`. `scanFiles()` is a `walkTopDown()` flow of `LibraryFileInfo` (grouped by parent dir); `openBytes()` reads a file by path; add/remove-location validate directories and persist the list to `locationsFile`. Also `addLocation(File)`. Internal `SOURCE_ID = "file"`. |

## Why depend on this module

Each platform's DI depends on `:real` to bind `LocalFileContentSource` as the
`LibrarySource`/`ContentSource` (picking the `locationsFile`: `workDir` on the
JVM, `context.filesDir` on Android). Elsewhere depend on
`:cbox:common:contentsource:api` for the interface; tests use
`:cbox:common:contentsource:fake`.

## Using it

```kotlin
val source = LocalFileContentSource(locationsFile = File(workDir, "library-locations.txt"))
source.addLocation(File("/storage/emulated/0/Music"))

source.scanFiles().collect { file -> /* feed RealScanner */ }
val bytes = source.openBytes("/storage/emulated/0/Music/game/track.spc")
```

## Module facts

- **Plugin:** `sage.kmp`
- **Targets:** Android + JVM
- **Source set:** `src/main/java` (`jvmSharedMain` — needs `java.io.File`)
- **SAGE/module dependencies:** `:cbox:common:contentsource:api`, `sage.common.logging`
