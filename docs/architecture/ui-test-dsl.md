# UI Test Scripting DSL — design & roadmap

Status: **in progress** (Phase 0 landed on JVM). This is a living plan; update it as phases land.

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

**Phase 0 — Foundation (prove the rails). — DONE on JVM (2026-06-14).**
Stood up `:cbox:common:uitest` applying `sage.kmp` + the catalog-sourced
`compose.compiler` / `compose.multiplatform` plugins (the latter is what exposes the
`compose.uiTest` and `compose.desktop.currentOs` accessors), plus `chipbox.kmp.test`. A
trivial "host a `Text`, assert displayed" `runComposeUiTest` test.

Findings:

- **JVM desktop: green.** `runComposeUiTest` hosts a composable and queries the semantics
  tree from this toolchain. The core rail is real. Build-script note: `compose.uiTest`
  requires `@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)`; the test
  body requires `@OptIn(ExperimentalTestApi::class)`.
- **Android host: blocked on Robolectric.** The same body throws `NullPointerException` at
  `setContent` on the `testAndroidHostTest` target — that target has no Android framework
  (Looper/Context). Robolectric supplies it, but Robolectric needs a `@RunWith` runner
  annotation, **which cannot live in `commonMain`/`commonTest`** where the shared DSL tests
  must go. No Robolectric is in either catalog today; AGP is 9.2.1 (KMP android-library +
  `withHostTest`). The smoke test is therefore scoped to `jvmTest` so the repo stays green.
- **Not yet exercised** (deferred until the harness composes real screens): the test main
  dispatcher, and whether `composeResources` strings load under `runComposeUiTest` (the
  Paparazzi classpath-reader override may recur).

**OPEN DECISION — Android-host execution strategy.** Two viable stances:
  1. *JVM desktop as the canonical CI rail* (+ optional on-device instrumented tests for
     Android-specific behavior). Common UI tests run on JVM (and later js); Android parity is
     by construction (same Compose runtime). Zero Robolectric. Simplest; matches how most CMP
     projects operate.
  2. *Invest in Robolectric for true Android-host execution.* Requires a Robolectric catalog
     entry, an android-test-only runner entry point (the `@RunWith` can't be common), AGP
     host-test resource config, and likely `@GraphicsMode(NATIVE)`. Heavier, bleeding-edge on
     AGP 9 KMP, but lets the *same* suite assert on the Android target on a host machine.

**Phase 1 — Test graph + shell harness.** Cross-platform `TestAppGraph`
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

**Phase 4 — Director (deferred).** Introduce a reified `DirectorCommand` sealed type +
a recording `Director` decorator bound in `TestAppGraph` (overriding `RealDirector`).
Implement `assertDirectorReceived(DirectorCommand.Play)`. Open question: reify commands
test-only or also land a thin command-emitting decorator in production. One-binding swap
thanks to the full-shell graph.

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
