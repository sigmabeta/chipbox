# `:cbox:common:uitest`

> Cross-platform Compose UI-test harness that drives the real Chipbox UI over fakes, on desktop and on-device.

A test-only module (no main source — a Gradle path with no `:api` suffix). It
hosts the **real** `ChipboxAppUi` shell over a Metro graph of **fake** bindings,
exposes a small DSL for scripting screens, and ships the actual UI test specs.
The specs live in `src/jvmTest` (so Android Studio shows and runs them) and the
`sage.compose.uitest` plugin mirrors them onto **both** the `jvmTest` (desktop
unit) and `androidDeviceTest` (instrumented) trees, so the same scripts run in
both places.

> **For agents:** you **may add** new UI tests, but you **must not modify** any
> existing test — assertions, data, name, or structure. The tests are the source
> of truth, owned by humans. If your change breaks one, stop and report it.

## Contents

**Base harness / DSL**
- `ChipboxUiTest.kt` — the entry point `runChipboxUiTest { ... }` and the `ChipboxUiTest` class. Launches the shell, then exposes the verbs specs use: navigation (`startAtScreen`, `startFromSession`), seeding (`seedGame`, `seedPlaylist`, `favoriteTrack`, lookups like `gameId`/`trackId`/`artistId`), scan driving (`beginScan`/`scanReadingFile`/`completeScan`/`failScan`), interaction (`click*`, `typeSearch`, `clickTag`), and typed assertions (`assertTitle`, `assert*ItemDisplayed`, `assertSectionHeader`, `assertNavigationEvent*`, `assertDirectorReceived*`, `assertStartedSession`).

**Test app graph + DI**
- `harness/TestAppGraph.kt` — a Metro `@DependencyGraph(AppScope::class)` (extends `ViewModelGraph`) that provides the leaf bindings a real screen's ViewModel needs, all backed by fakes: `RandomMemoryRepository` (deterministic seed-1234 library of 10 games / 50 tracks / 5 artists), `FakeDirector`, `CountingScanner`, fake favorites/playlists/settings/debug repos, a `FakeFileSystem`, and the **real** `ChipboxStringProvider` (preloaded via `loadChipboxStrings()`). `createTestAppGraph()` builds a fresh one per test.
- `harness/TestMetroViewModelFactory.kt` — the test-graph `MetroViewModelFactory` backing `metroViewModel<T>()` in hosted screens; same shape as the apps' factories, populated by every `@ContributesIntoMap` ViewModel on the test classpath.

**Failure artifacts**
- `harness/FailureArtifacts.kt` — on any failure, dumps a semantics-tree text file and a PNG screenshot of the live scene before rethrowing (PNG encoded with only `java.util.zip`/`java.io` so it works on both JVM and device). Best-effort, never masks the real failure; paths logged via `Hatchet`.

**Platform seam** (`expect`-style, one actual per target)
- `harness/PlatformSeam.kt` (`jvmTestPlatform`, desktop) and `harness/PlatformSeam.kt` (`androidDeviceTest`, on-device) — `platformArtifactDir()` (where failure artifacts go: desktop `build/` dir / tmpdir, device AGP `additionalTestOutputDir` / external files) and `platformTestArgument(key)` (system property on desktop, instrumentation arg on-device). The desktop seam is in `src/jvmTestPlatform` and added only to `jvmTest`, not mirrored to the device tree.

**Test specs** (`src/jvmTest/*Test.kt`, mirrored to `androidDeviceTest`)
- The actual UI test cases — do not modify them. They cover browse flows (`BrowseAllTracksTest`, `BrowseByArtistTest`, `BrowseByGameTest`, `BrowseByPlatformTest`, `GamesForPlatformTest`, `GameDetailHarnessTest`), playlists (`PlaylistsTest`, `PlaylistDetailTest`, `AddToPlaylistTest`), playback (`NowPlayingTest`, `PlaybackStatusTest`, `DirectorRequestTest`), `SearchTest`, `FavoritesTest`, `SettingsTest`, library/management (`LibraryTest`, `ManageLibraryTest`, `FolderPickerTest`), scan status (`RescanStatusTest`, `ScanStatusModuleTest`), debug screens (`CrashLogTest`, `ErrorLogTest`, `ComponentLibraryTest`/`ComponentLibraryModeTest`), the full shell (`FullShellHarnessTest`, `StartAtScreenTest`, `SmokeTest`), and supporting fixtures (`SectionHeaderTest`, `ClickItemTest`, `SecondScreenDataTest`, `RandomMemoryRepositoryTest`).

## Why depend on this module

You don't depend on `:cbox:common:uitest` — nothing consumes it; it is the test
module itself. It pulls in `appui.api` (which `api`-exposes every feature `:real`
ViewModel) plus the `:fake` repositories and the real string provider, so the
`TestAppGraph` can satisfy every hosted screen's dependencies.

## Running it

```sh
# Desktop (JVM unit-test tree)
./gradlew :cbox:common:uitest:jvmTest

# On-device (instrumented), with an observe-delay to watch each action
./gradlew :cbox:common:uitest:connectedAndroidDeviceTest -Pchipbox.uitest.actionDelayMs=1500
```

A spec, for reference (do not edit existing ones):

```kotlin
@Test
fun opensGameDetail() = runChipboxUiTest {
    startAtScreen(GameDetail(gameId("Iron Quest")))
    assertTitle("Iron Quest")
    assertSectionHeader("Songs")
}
```

`-Pchipbox.uitest.actionDelayMs` inserts a real pause before each click and at
the end of a test (routed in as a JVM system property on desktop and an
instrumentation arg on-device). On desktop, failure artifacts land in
`build/uitest-failures`.

## Module facts

- **Plugin:** `sage.kmp` + `sage.compose.uitest` (the shared rails) + `metro` +
  `chipbox.plugins.kmp.test`
- **Targets:** Android device-test (`androidDeviceTest`) + JVM (`jvmTest`); no JS
- **Source set:** `jvmTest` (canonical specs + harness, mirrored to
  `androidDeviceTest`), `jvmTestPlatform` (desktop platform seam, JVM only),
  `androidDeviceTest` (device platform seam)
- **SAGE/module dependencies:** `appui.api`; feature `:real`/`:api`
  (`gameDetail`, `artistDetail`, `favorites`, `playlists`, `playlistDetail`);
  the `:api` + `:fake` pairs for repository, history, favorites, playlists,
  director, settings, contentsource, scanner, debug, debugInfo; `strings:api` +
  `strings:real`; SAGE `di`/`logging`/`appinfo`/`ui.strings`/`ui.perfCompose`,
  `metrox.viewmodel`, lifecycle-runtime-compose, okio fake filesystem
