# `:apps:cli`

> Headless Mordant TUI for scanning, browsing, and organizing a Chipbox library on the desktop.

A JVM command-line app that drives the real Chipbox scan + library stack from an
interactive terminal menu. Unlike the GUI apps it does **not** use Metro — it
hand-assembles the slice it needs (`ChipboxLibrary`). It's a leaf at the top of
the graph: nothing depends on it.

## What this builds / how to run it

- **App target:** the `chipbox-cli` application (`mainClass =
  net.sigmabeta.chipbox.cli.MainKt`), built via the Gradle `application` plugin.
- **Run:** `./gradlew :apps:cli:run --console=plain` forks a JVM with `System.in`
  wired through for Mordant's raw-mode picker. The reliable way to drive the TUI
  is the installed distribution:
  `./gradlew :apps:cli:installDist` →
  `apps/cli/build/install/chipbox-cli/bin/chipbox-cli`.
- **Native lib:** the scanner probes streamed-audio files through `vgmstream`
  (`VgmstreamProbe → System.loadLibrary("vgmstream")`) — the only native lib the
  scan path touches. The build reuses `:apps:jvm:buildHostNativeVgmstream`'s
  `libvgmstream.so` and points `java.library.path` at it (bundled into
  `lib/native/` in the distribution). Every chiptune format is parsed by
  pure-Kotlin readers.
- The CLI never renders Compose; the build excludes the transitively-pulled
  Compose/skiko groups from the runtime classpath to keep the distribution slim.

## Entry point

`Main.kt`'s `main()` opens a Mordant `Terminal`, resolves the per-OS
`appDataDir("chipbox-cli")`, constructs `ChipboxLibrary(workDir)`, and runs
`MainMenu`. `MainMenu` loops over the actions that currently apply (View needs a
scanned DB, Rescan needs a saved location, Organize/cover-art need both; Add /
Remove / Exit are always offered).

## Contents

| File | What it is |
| --- | --- |
| `Main.kt` | Entry point: terminal + `appDataDir` + `ChipboxLibrary` + `MainMenu`. |
| `ChipboxLibrary.kt` | Hand-wired scan/library stack: Room (bundled SQLite) → `DatabaseRepository`, `LocalFileContentSource`, `RealScanner`. Exposes scan + query helpers. |
| `MainMenu.kt` | Top-level menu loop; dispatches to the action handlers. |
| `LibraryMenu.kt`, `LibraryBrowser.kt`, `LibraryFolderPicker.kt` | Browse games / artists / platforms / tracks; the interactive folder picker. |
| `LibraryScan.kt` | Runs a scan, reporting per-game progress. |
| `GetCoverArt.kt`, `OverrideCoverArt.kt` | IGDB cover-art fetch and manual game→IGDB link override (build the OkHttp client/IGDB client). |
| `OrganizeLibrary.kt` | Plans/applies an on-disk library reorganization via the shared organizer. |
| `CliStrings.kt`, `CliStringLoader.kt` | Reads platform/section display names straight from the packaged `.cvr` string resources (no Compose `getString`). |

## Using it

`ChipboxLibrary` is the reusable core if you want the scan/query stack without
the menu:

```kotlin
val library = ChipboxLibrary(workDir = appDataDir(xdgName = "chipbox-cli", nativeName = "Chipbox CLI"))
library.addLibraryFolder(File("/music/chiptunes"))
val finalState = library.scan { name, trackCount -> println("Found $name ($trackCount tracks)") }
val games: List<Game> = library.games()
library.close()
```

## Module facts

- **Plugin:** `sage.jvm` + `application`.
- **Targets:** JVM only.
- **Source set:** `src/main/java` (`jvmSharedMain`).
- **SAGE/module dependencies:** `cbox/common` `scanner`, `repository`,
  `database`, `contentsource(.file)`, `readers`, `models`, `utils`, `strings`,
  `coverart`, `organizer`, `player.emulators.vgmstream.real` (api + real splits);
  `sage.common.logging` / `ui.strings`; Mordant, bundled SQLite, kotlinx
  coroutines, OkHttp, okio. Build-time: `:apps:jvm:buildHostNativeVgmstream`.
