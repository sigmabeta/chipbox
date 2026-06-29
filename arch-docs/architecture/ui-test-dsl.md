# UI Test Scripting DSL — design & roadmap

Status: **in progress** (Phase 0 landed: JVM host runs, Android device-test builds). This is a
living plan; update it as phases land.

## Where this lives: sage, not chipbox

Chipbox will not be the last sage app that needs UI testing, so the **generic rails are
intended to be a sage capability**, reusable across sage apps:

- **sage** owns: the `runComposeUiTest` wiring (a `sage.compose.uitest` convention plugin in
  sage-build-logic), the `jetbrains-compose-ui-test` catalog coordinate, and the
  app-agnostic harness/DSL primitives (selector-by-semantics helpers, the recorder, the
  fixture-seeding scaffold, the navigation-effect tap as an interface).
- **chipbox** owns only its bindings: `ChipboxAppUi`, the concrete route keys
  (`GameDetail`/`ArtistDetail`), `ListModel` item types, and the test app graph.

The Phase 0 spike currently lives at `:cbox:common:uitest` (chipbox) to prove the rails
fast; once green across the strategy below, the generic half migrates into sage and chipbox
keeps a thin app-specific module on top.

## Goal

A cross-platform UI test DSL that lets a test read like:

```kotlin
fun test_whenArtistClickedArtistIsShown() {
    startAtScreen(GameDetail(1980))
    assertTitle("Metal Slug")

    clickWideItem(name = "JIM")

    assertNavigationEvent(ArtistDetail(3023))
    assertTitle("JIM")
}

fun test_whenSongClickedPlaybackStarts() {
    startAtScreen(GameDetail(1980))
    assertTitle("Metal Slug")

    clickNameCaptionValueItem(name = "Stage 1")

    assertDirectorReceived(DirectorCommand.Play)
}
```

The DSL surface lives in `commonTest` and runs the *same* code on Android, desktop
JVM, and js/wasm, delegating to platform-specific implementations only where
necessary (test bootstrap).

## The seams the DSL taps (verified 2026-06-13)

| DSL verb | Seam | Readiness |
|---|---|---|
| `startAtScreen(GameDetail(1980))` | Route keys are `@Serializable data class GameDetail(val id: Long)` (feature `:api`); `screenFor(key)` → Voyager `Screen`; VM built via assisted factory from a fake/in-memory repository | exists |
| `assertTitle("Metal Slug")` | `GameDetailState.title()` → `TitleBarModel(game.title)` → real `LocalTitleBarController` | exists |
| `clickWideItem` / `clickNameCaptionValueItem` | 16 item composables dispatched by `ListModel.Content()`'s `when(model)`; click → `ActionSink.sendAction` → `VM.handleAction`. **No testTags/semantics today.** | needs a semantics seam |
| `assertNavigationEvent(ArtistDetail(3023))` | Central seam: every nav flows through `ChipboxAppUiViewModel.handleEvent` → `effects: SharedFlow<ChipboxEvent.NavigateTo(destination)>`, carrying the typed route key | exists |
| `assertDirectorReceived(DirectorCommand.Play)` | `Director` is an interface of imperative methods (`play()`, `start(...)`). **No `DirectorCommand` type exists.** | net-new — deferred to Phase 4 |

Key reference points in the codebase:

- Route keys: `features/*/api/.../<Route>.kt` (e.g. `GameDetail(val id: Long)`,
  `ArtistDetail(val id: Long)`).
- `screenFor(destination)`: `cbox/common/appui/api/.../ChipboxScreens.kt`.
- Navigation seam: `ChipboxAppUiViewModel.handleEvent` / `effects`
  (`cbox/common/appui/api/.../ChipboxAppUiViewModel.kt`); event type
  `ChipboxEvent.NavigateTo(destination: Any)` in
  `cbox/common/appcomm/api/.../ChipboxEvent.kt`.
- Item dispatch: `ListModel.Content()` `when(model)` in
  `cbox/common/ui/components/api/.../ComposableMapping.kt`.
- Click path: `ActionSink.sendAction` → `ChipboxListViewModel.sendAction` →
  `handleAction` (subclass).
- Data seeding: `MemoryRepository.upsertGame(RawGame)` in
  `cbox/common/repository/fake/.../memory/MemoryRepository.kt`.
- Director: `cbox/common/player/director/api/.../Director.kt` (interface);
  `FakeDirector` in the `:fake` module records calls as counters/lists.

## Cross-platform foundation

The engine is Compose Multiplatform's **`runComposeUiTest { }`** (`compose.uiTest`),
which runs identical `commonTest` code across targets. It is declared in the version
catalog (`androidx-compose-ui-testing` = `ui-test-junit4`) but currently **unused** —
wiring it up is greenfield.

## Decisions taken

- **Selector strategy: add a semantics seam.** A single `Modifier.semantics` at the
  central `ListModel.Content()` dispatch attaches each item's model-type name +
  `dataId`. Makes the typed selectors (`clickWideItem` vs `clickNameCaptionValueItem`)
  real and unambiguous. One shared-code touch, inert in production.
- **Hosting fidelity: full `ChipboxAppUi` shell.** `startAtScreen` boots the entire
  tabs shell + Voyager Navigator via a cross-platform Metro test graph and drives
  navigation to `screenFor(route)`. Highest fidelity (real Voyager push/back, real
  `TitleBarController`); the real DI graph means the Director slots in at Phase 4 as a
  one-binding swap.

## Architecture

New test-support module `:cbox:common:uitest` (in `commonMain`, consumed by feature
`commonTest` source sets — same shape as the `:fake` modules and `previews`):

1. **DSL surface** — a `ChipboxUiTest` receiver scope: `startAtScreen`, `assertTitle`,
   `clickWideItem`, `clickNameCaptionValueItem`, `assertNavigationEvent`, …
2. **Harness** — boots the real `ChipboxAppUi` inside `runComposeUiTest`, wired to a
   test graph, holding a handle on `ChipboxAppUiViewModel` (nav seam) + a recorder.
3. **`expect/actual` shim** — per-platform bootstrap only (install test main
   dispatcher, construct the platform graph). Everything else is common.

## Phases

**Phase 0 — Foundation (prove the rails). — DONE (2026-06-14): JVM host runs, Android
device-test builds.** Stood up `:cbox:common:uitest` applying `sage.kmp` + the catalog-sourced
`compose.compiler` / `compose.multiplatform` plugins (the latter is what exposes the
`compose.uiTest` and `compose.desktop.currentOs` accessors), plus `chipbox.kmp.test`. A
trivial "host a `Text`, assert displayed" `runComposeUiTest` test, in a shared `uiTest` source
set that runs on both the JVM host and a real Android device.

Findings:

- **JVM desktop: green.** `runComposeUiTest` hosts a composable and queries the semantics
  tree from this toolchain. The core rail is real. Build-script note: `compose.uiTest`
  requires `@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)`; the test
  body requires `@OptIn(ExperimentalTestApi::class)`.
- **Android: on-device (instrumented), NOT host.** The same body NPEs at `setContent` on the
  `androidHostTest` target — that target has no Android framework (Looper/Context). Rather than
  fake one with Robolectric (which needs a `@RunWith` runner annotation that **can't live in
  `commonMain`/`commonTest`**), the Android half runs on a real device/emulator, where
  `runComposeUiTest` has a true framework. Enabled with `withDeviceTest { instrumentationRunner
  = "androidx.test.runner.AndroidJUnitRunner" }` in the module's `android { }` block (AGP 9.2.1
  KMP android-library). The device-test APK assembles (`uitest-androidTest.apk`); the on-device
  run is `./gradlew :cbox:common:uitest:connectedAndroidDeviceTest` with a device attached.
- **Not yet exercised** (deferred until the harness composes real screens): the test main
  dispatcher, and whether `composeResources` strings load under `runComposeUiTest` (the
  Paparazzi classpath-reader override may recur).

### Source-set topology (decided)

One spec, two targets — JVM desktop host + Android device. The KMP gotcha: `jvmTest` is in the
**unit-test tree** and `androidDeviceTest` is in the **instrumented-test tree**, and a source set
may not `dependsOn` sets from two different trees (Gradle errors: *"Invalid Source Set Dependency
Across Trees"*). So the bridge is NOT `dependsOn` — instead:

- The shared specs live in the JVM unit-test tree's **canonical** `src/jvmTest/kotlin`. This is the
  source set's own default dir, so Android Studio reliably marks it a test source root (run gutters,
  debug) — a neutral dir owned by *neither* source set (e.g. `src/uiTest`) is dropped on the import
  conflict and the tests vanish from the IDE.
- The Android instrumented tree **mirrors** them: `androidDeviceTest { kotlin.srcDir("src/jvmTest/kotlin") }`.
  Same physical files, two independent compilations, one per tree. The specs use only multiplatform
  APIs (`compose.uiTest`, `compose.material3`, `kotlin.test`) available to both. (Caveat: a desktop-only
  test added to `src/jvmTest` would also compile on-device — keep this source set cross-target.)
- The dir is kept **off `androidHostTest`** on purpose (no Android framework there → NPE).
- Deps per tree: `jvmTest` inherits `compose.uiTest`/`compose.material3`/`kotlin.test` from
  `commonTest` (unit-test tree) and adds `compose.desktop.currentOs`. `androidDeviceTest`
  inherits nothing from `commonTest`, so it re-declares those three plus the device-only deps:
  `ui-test-manifest` (empty host Activity), `androidx.test:runner` (AndroidJUnitRunner),
  `androidx.test.ext:junit`.

CI: `jvmTest` is the fast host rail (runs everywhere, no device); `connectedAndroidDeviceTest`
is the Android rail (needs a device/emulator). When the generic rails move into sage, this
topology + the `withDeviceTest` wiring belong in the `sage.compose.uitest` convention plugin.

**Phase 1 — Test graph + shell harness. — IN PROGRESS.**

Step 1 (DONE, JVM, 2026-06-14): the hard machinery proven on a single real screen.
`TestAppGraph` (a `@DependencyGraph(AppScope::class)` extending `ViewModelGraph`, with
`TestMetroViewModelFactory`) compiles and instantiates *in the test source set* — confirming
Metro processes test compilations. It binds fakes (`MemoryRepository` exposed for seeding,
`FakeDirector`, stub `StringProvider`/`Hatchet`). `GameDetailHarnessTest` seeds a game via
`upsertGame`, hosts the real `GameDetailRoute` in `runComposeUiTest` (providing
`LocalMetroViewModelFactory`, a plain `LocalViewModelStoreOwner` — the app gets one from the
Compose Window/Voyager, absent here — plus `LocalTitleBarController`/`LocalChipboxStringProvider`/
`LocalLogger`), and asserts the seeded "Metal Slug"/"Stage 1" surface. The real
`GameDetailViewModel` (assisted) resolves through the ViewModel factory. Lives in `jvmTest` for
now.

Step 2 (DONE, JVM, 2026-06-14): the full `ChipboxAppUi` tabs shell hosts over `TestAppGraph`.
The "every feature VM's deps" worry turned out small — beyond the obvious leaves, Metro listed
exactly six more (`LibrarySource`, `CrashReportStore`, `DebugSettingsManager`, `DebugInfoManager`,
`Scanner`, `okio.FileSystem`), all covered by existing fakes / a 2-line stub. So the hand-rolled
`TestAppGraph` stays viable — no need to reuse the real per-platform app graph. `FullShellHarness-
Test` hosts the shell, pulls the shell's own `ChipboxAppUiViewModel` back out of the provided
`LocalViewModelStoreOwner` (same instance, via `ViewModelProvider.create(owner, factory)`), fires
`ChipboxEvent.NavigateTo(GameDetail(id))`, and the real Voyager push renders the seeded screen
("JIM"/"Stage 1").

Step 3 (DONE, JVM, 2026-06-14) — in-tab navigation seam, so `assertTitle` works. The `TopAppBar`
is composed *only* in `ChipboxTabsScreen`, and the app VM routes `NavigateTo` to the **outer**
Navigator (which unmounts the tabs scaffold), so a screen pushed that way has no title bar. Added a
small public seam to `appui.api`: `ChipboxAppUi(activeTabDestinations: Flow<Any>? = null)` —
route-key destinations pushed onto the **active tab's** Navigator (via `screenFor`), the same place
an in-tab `NavigateTo` lands, so the screen renders with chrome. (This is test-support first; a
future deep-link / session-restore could reuse it. The apps pass nothing → no behaviour change.)
`FullShellHarnessTest` now emits `GameDetail(id)` into that flow and asserts both the top-bar title
("Metal Slug") and the content ("JIM"/"Stage 1").

Step 4 (DONE, JVM, 2026-06-14) — the DSL surface. `runChipboxUiTest { }` exposes a
`ChipboxUiTest` receiver scope over the harness, hosting the real shell over a `TestAppGraph`
that defaults to a populated `RandomMemoryRepository` (so screens have content out of the box).
Verbs implemented and tested on JVM:

- `startAtScreen(route)` — opens a route inside the active tab via the `activeTabDestinations`
  seam (so the chrome is present).
- `assertTitle(text)` — the chrome's top-bar title.
- `clickWideItem(name)` / `clickNameCaptionValueItem(name)` — select by item type + name. Backed
  by the **semantics seam**: `ListModel.Content()` tags each item with its model `simpleName`
  (`Modifier.testTag`, riding on the row's clickable modifier so it merges with the text). Pixel-
  inert; Paparazzi confirms.
- `assertNavigationEvent(route)` — backed by a second seam, `ChipboxAppUi(onNavigate)` /
  `LocalNavigationObserver`, invoked on each active-tab deep push (in-tab `NavigateTo` doesn't go
  through the app VM's effects). Route keys are data classes, so it matches on value.
- helpers: `firstGame()` / `artistId(name)` (read the populated library), `seedGame(...)` (add a
  known fixture), `assertDisplayed(text)`.

`StartAtScreenTest` (startAtScreen + assertTitle off the random library) and `ClickItemTest`
(clickWideItem → assertNavigationEvent + assertTitle) read like the target examples.

Phase 4 — `assertDirectorReceived` (DONE, JVM, 2026-06-14). The deferred Director work landed in
two parts: (1) a separate refactor reified the `Director` control surface as a `SessionRequest`
sealed interface **in production** (`Director.request(SessionRequest)`), so the open "test-only vs
production decorator" question is moot — `FakeDirector` records `requests: List<SessionRequest>`;
(2) `TestAppGraph` exposes its `FakeDirector`, and `ChipboxUiTest` gains
`assertDirectorReceived(request)` (by value, e.g. `SessionRequest.Play`) and a reified
`assertDirectorReceived<SessionRequest.Start>()` (by type, for requests carrying a `Session`).
`DirectorRequestTest` clicks a song from the populated library (no seeding; type-agnostic `click`)
and asserts the Director received a `Start`. Also added: `seedGame(artists = …)` (multi-artist),
`firstGame()` now loads tracks (via per-id `getGame`, dodging `getAllGames`' load-once flag).

Step 5 — on-device lift (DONE, 2026-06-14). The harness + all scripts stay in the canonical
`src/jvmTest/kotlin`, and `androidDeviceTest` mirrors them via `srcDir`, so the *same* files compile
into both the `jvmTest` (unit-test tree) and `androidDeviceTest` (instrumented tree) source sets. The ~25 harness deps are
shared via a `harnessDependencies` lambda applied to both trees (they can't `dependsOn` across
trees). No per-target graph builder was needed — the Metro `TestAppGraph` compiles for the Android
target unchanged (all binding deps are KMP/android-compatible). Verified: `jvmTest` runs all 10
scripts green; `assembleAndroidDeviceTest` builds `uitest-androidTest.apk` (the whole harness +
graph compiles + packages for on-device). The actual device run is
`./gradlew :cbox:common:uitest:connectedAndroidDeviceTest` with a device/emulator attached (not run
here — no device).

Generic rails moved into sage. **DONE.** `SageComposeUiTestModulePlugin` (id `sage.compose.uitest`,
in `sage-build-logic/convention`) now owns the cross-app wiring: it applies the Compose compiler +
JetBrains Compose plugins, declares the on-device `withDeviceTest` component, mirrors the canonical `src/jvmTest/kotlin`
specs onto `androidDeviceTest` via `srcDir`, and carries the compose-test + instrumentation deps
(`compose.uiTest`/`compose.material3`/`compose.desktop.currentOs` via `ComposePlugin.Dependencies`,
plus `ui-test-manifest`/`test.runner`/`test.ext.junit`). It reads compose deps from the Compose
Gradle plugin, so the catalog gained `compose-multiplatform-gradlePlugin`
(`org.jetbrains.compose:compose-gradle-plugin`) on the build-logic runtime classpath. The chipbox
`:cbox:common:uitest` build now just `alias(libs.plugins.sage.compose.uitest)` + the app-specific
`harnessDependencies` (the DI graph's feature modules + fakes — `TestAppGraph`/`ChipboxUiTest` stay
chipbox-specific). Verified: `jvmTest` green, `assembleAndroidDeviceTest` builds, through the plugin.

On-failure diagnostics. **DONE.** `runChipboxUiTest` wraps the spec body in a try/catch that, on any
failure (AssertionError included), dumps two artifacts from the live scene before rethrowing
(`harness/FailureArtifacts.kt`): a **semantics-tree dump** (`onRoot().printToString()` — the most
useful, since the usual failure is "a matcher found nothing") and a **PNG screenshot** of the root.
Both are best-effort (wrapped so a capture problem can't mask the real failure) and logged through the
graph's real `BasicHatchet` (replaced the no-op `StubHatchet`). The PNG encoder is pure
`java.util.zip` over `ImageBitmap.toPixelMap()` — no `toAwtImage`/`asAndroidBitmap` — so the *same*
code runs on desktop (`jvmTest`) and on-device (`androidDeviceTest`). The artifact location is the one
place that *is* per-target (a small `platformArtifactDir()` seam — desktop impl in `src/jvmTestPlatform`,
device impl in `src/androidDeviceTest`, encoding stays shared): desktop writes to the module's
`build/uitest-failures` (the `jvmTest` task sets `chipbox.uitest.artifactDir` there); on-device it writes
to AGP's `additionalTestOutputDir`, which `connectedAndroidDeviceTest` pulls back to the host's
`build/outputs/connected_android_test_additional_output/.../<device>/` (verified on a connected device),
falling back to the app's external files dir if AGP doesn't supply it. So both rails leave artifacts under
`build/` — CI-collectable. This is failure-only and never *compares*, so it has no determinism/cross-platform
tax. The tree-printer here is the same machinery the snapshot followup below would reuse.

Observe delay. **DONE.** `-Pchipbox.uitest.actionDelayMs=<ms>` inserts a real wall-clock pause before
each click verb (`clickWideItem`/`clickNameCaptionValueItem`/`click`) and once before the container
ends, so a device run can be watched action-by-action (the device UI thread keeps rendering during the
`Thread.sleep`, so the previous action's settled result stays on screen). Zero/unset → no-op. The one
flag reaches both targets: the build sets it as a system property on `jvmTest` and as an
instrumentation runner argument on `androidDeviceTest` (the device test is a separate process that
never sees host system properties) via `compilations.withType(KotlinMultiplatformAndroidDeviceTestCompilation)`
— `withDeviceTest` can't be called twice. The harness reads it through the same `platformTestArgument`
seam (`PlatformSeam.kt`) that resolves the artifact dir. Verified +3.0s for two pauses on both desktop
and device.

Real strings + section-header assertions. **DONE.** The harness now binds the real
composeResources-backed `ChipboxStringProvider` (preloaded via `runBlocking { loadChipboxStrings() }`
in `TestAppGraph`, exactly like the production apps — the preload happens outside composition, so
`runComposeUiTest` never resolves a composeResource mid-render, which is what the old empty-string stub
existed to avoid). Screens now render real text, so `assertSectionHeader(text)` can check the detail
screens' headers (GameDetail → "Songs"/"Artists"; ArtistDetail → "Songs"/"Games"). The verb scrolls the
**innermost** vertical scroller (the detail screen nests a content scroller inside an outer page
scroller) and matches the tagged `SectionHeaderListModel` row by a *descendant* carrying the text
(`SectionHeader` sets `heading()` but doesn't merge its title `Text` up). Verified on desktop + device.

Remaining (lower priority):
- Optional: add the item `dataId` to the semantics seam for disambiguating same-name rows.
- **Semantic-snapshot verification (followup).** A `assertMatchesSnapshot(name)` verb that dumps the
  current **semantics tree** (node text/role/structure, canonicalized to a string) and diffs it
  against a stored golden — record/compare like Paparazzi, but over the semantics tree, not pixels.
  Rationale: it regression-guards the *interactive, mid-flow* states the harness uniquely reaches
  (post-click, post-nav, post-Director-request) that Paparazzi structurally can't (it renders static
  composables in isolation — no DI graph, nav, or interaction). Preferred over `captureToImage()`
  pixel goldens here because a semantics dump is **deterministic and cross-platform-stable** (byte-
  identical on `jvmTest` desktop Skia and `androidDeviceTest`), whereas pixels diverge by renderer/
  host fonts/AA and would need per-platform golden sets. Keep Paparazzi for pixel fidelity of a
  single screen; use semantic snapshots for flow/state regressions. Needs: a canonical tree printer,
  a record/verify mode (env flag, goldens in-repo), and animation/clock pinning (the seeded
  `RandomMemoryRepository` already covers data determinism).

Original Phase 1 plan: cross-platform `TestAppGraph`
(`@DependencyGraph(AppScope::class)`) aggregating the *common* `@ContributesTo(AppScope)`
binding containers, so `MemoryRepository` is the bound `Repository` and `RealDirector`
the `Director`. Host real `ChipboxAppUi` with `LocalMetroViewModelFactory` from it.
A fixture-seeding DSL (`fixtures { game(1980, "Metal Slug") { artist(3023, "JIM");
track("Stage 1") } }`) loads `MemoryRepository` before `startAtScreen` drives Voyager to
the screen. `assertTitle` reads the real `TitleBarController`. Risk: every `AppScope`
contribution must be KMP-safe (no Android-only binding leaking in).

**Phase 2 — Semantics seam + item selectors.** Add the `Modifier.semantics`
(model-type + `dataId`) at `ListModel.Content()`. Implement `clickWideItem`,
`clickNameCaptionValueItem`, and a general `clickItem(type, name)` as
`hasTestTag(type) and hasText(name)`. Verify the click drives the real
`ActionSink → VM.handleAction` path.

**Phase 3 — Navigation assertions.** Record `ChipboxAppUiViewModel.effects` into the
harness recorder. Implement `assertNavigationEvent(route)` as a typed match on
`ChipboxEvent.NavigateTo(destination)`. The real Voyager push corroborates; the effects
flow is the precise, race-free assertion point.

**Phase 4 — Director. DONE** (see the Phase 1 "Phase 4 — `assertDirectorReceived`" note above).
Resolved better than planned: the control surface was reified as `SessionRequest` in production, so
no test-only `DirectorCommand` or decorator was needed — `FakeDirector.requests` is the recording,
and `assertDirectorReceived` reads it.

**Phase 5 — Rollout.** Per-feature example tests, ergonomic helpers, docs, CI.

## Cross-cutting risks

- **Test main dispatcher** — Voyager + VMs assume `Dispatchers.Main`; the harness must
  install a test main (validated in Phase 0).
- **`composeResources` under `runComposeUiTest`** — may need the classpath-reader
  override Paparazzi needed (see `paparazzi_compose_resources`).
- **Android target** — `runComposeUiTest` on Android wants instrumentation or
  Robolectric; Phase 0 settles which.
- **js target** — gated out of CI (`-Pchipbox.js=true`), so compile/run it explicitly
  when touching shared test code.
