# UI tests — guide for agents

How to work with Chipbox's cross-platform UI tests (`:cbox:common:uitest`). Read
the rules first — they are not optional. The design/internals live in
[`ui-test-dsl.md`](ui-test-dsl.md); this doc is the day-to-day how-to and policy.

## Rules (read first)

- ✅ **You MAY add new UI tests** for new features and screens. Adding coverage is
  encouraged — follow the patterns below.
- ⛔ **You MUST NOT modify an existing UI test.** The tests are the **source of
  truth** for intended behaviour and are owned by humans. This is absolute:
  - Don't change a test's assertions, the data it references, its name, or its
    structure. Don't delete one. Don't weaken or `@Ignore` one.
  - **Especially** don't edit a test to make it pass. If your change makes an
    existing test fail, that's the test telling you your change is wrong or needs
    a human decision — **stop and report it to the human**, with the failing test
    and the failure artifact (see below). Do not "fix" the test.
  - Only a human decides to change a test. If a human explicitly tells you to edit
    a specific existing test, you're acting as their hands — but you never do it on
    your own initiative.

Why: these tests encode what the app is supposed to do. An agent silently
relaxing one to get a green run destroys that signal.

## What the harness is

- Module `:cbox:common:uitest`. Specs live in
  `src/jvmTest/kotlin/net/sigmabeta/chipbox/uitest/` and are mirrored onto
  `androidDeviceTest`, so the **same specs run on the desktop JVM and on a real
  Android device**.
- Entry point `runChipboxUiTest { ... }` hosts the *real* `ChipboxAppUi` shell
  over a fake DI graph (`TestAppGraph`): a deterministic in-memory library
  (`RandomMemoryRepository`, seed 1234 → 10 games / 50 tracks / 5 artists), a
  `FakeDirector` that records `SessionRequest`s, the real string provider, and the
  **fake image loader** (deterministic gradients, no network/Coil).

## Writing a new test

One file per screen, `<Screen>Test.kt`. For each screen: **launch it, assert its
content, then one test per available action** asserting the navigation or
`SessionRequest` that action produces. Example:

```kotlin
class BrowseByArtistTest {
    @Test fun listsArtists() = runChipboxUiTest {
        startAtScreen(BrowseByArtist)
        assertGridImageItemDisplayed("Jake Shimomura")
    }

    @Test fun tappingArtistOpensDetail() = runChipboxUiTest {
        startAtScreen(BrowseByArtist)
        click("Jake Shimomura")
        assertNavigationEvent(ArtistDetail(artistId("Jake Shimomura")))
    }
}
```

### The DSL verbs

- **Launch / navigate:** `startAtScreen(routeKey)`.
- **Click:** `clickWideItem(name)`, `clickNameCaptionValueItem(name)`,
  `click(text)` (generic), `clickFirstCardInHomeSection(name)` (Home carousels).
- **Assert content (prefer the typed verbs):** `assertWideItemDisplayed`,
  `assertGridImageItemDisplayed`, `assertIconNameItemDisplayed`,
  `assertCtaDisplayed`, `assertEmptyStateDisplayed`, `assertSingleTextItemDisplayed`,
  `assertNameCaptionItemDisplayed`, `assertNameCaptionValueItemDisplayed`,
  `assertSectionHeader`, `assertTitle`. Each scopes the match to a list-model type,
  so the same text in a different row type can't collide. `assertDisplayed(text)`
  exists as a generic fallback — avoid it.
- **Assert navigation:** `assertNavigationEvent(route)` (exact),
  `assertNavigationEventOfType<Route>()` (when the args aren't predictable).
- **Assert playback:** `assertDirectorReceived(SessionRequest.X)` or
  `assertDirectorReceived<SessionRequest.Start>()`.
- **Text input:** `typeSearch(query)`.

### Base test content on what the repository actually provides

The library is deterministic (seed 1234). **Reference the real data by name** —
`gameId("Iron Quest")`, `artistId("Jake Shimomura")` — rather than "whichever is
first". Known values today include game **"Iron Quest"** (tracks "Castle 36"…,
artists "Jake Shimomura"/"Michiko Tanaka"), games "Mega Dungeon", "Silent Saga",
artists "Jake Shimomura", "Saki Shimomura", etc.

- **Do not inject fixtures by default.** `seedGame(...)` exists but is not the
  default path — write tests against the existing library.
- **To discover the real data:** write the test, run it once, let the assertion
  fail, and read the failure dump (it contains the screenshot + the full semantics
  tree). Then assert the actual values you saw.

## Failure artifacts

On any failure the harness writes a PNG screenshot + a semantics-tree dump of the
live scene (named `<TestClass>.<method>.png` / `…-semantics.txt`):

- **desktop:** `cbox/common/uitest/build/uitest-failures/`
- **on-device:** pulled back to
  `cbox/common/uitest/build/outputs/connected_android_test_additional_output/androidDeviceTest/connected/<device>/`

Read the `Failure:` line and the tree to see exactly what rendered.

## Running

```sh
./gradlew :cbox:common:uitest:jvmTest                    # desktop, headless, fast
./gradlew :cbox:common:uitest:connectedAndroidDeviceTest # on a connected device
```

- Agents: run `jvmTest` to verify your work; hand on-device runs to the human (per
  `CLAUDE.md` — don't boot an AVD).
- `-Pchipbox.uitest.actionDelayMs=1500` inserts a pause before each click and at
  the end, so a human can watch a device run.

## Platform gotchas

- The desktop test window (1024×768, wide) is **not** the device viewport. Android
  injects **real** touch events, so a node must be **on-screen** to be clickable —
  an off-screen node gives `Failed to inject touch input`, while the desktop click
  is lenient. A test can pass on `jvmTest` and fail on device for this reason.
- Don't click an item **by name** in a horizontally-scrolled, viewport- or
  date-dependent carousel (e.g. Home "games of the day", whose order is shuffled
  per day) — the card may be off a narrow device's viewport. Use a position-based
  helper like `clickFirstCardInHomeSection(...)` and a type-only nav assertion.
- composeResources keep XML `\"` escapes literally on the JVM (the rendered string
  has backslashes), so match what the failure dump actually shows.
- Some fake data sources are stubs (e.g. platform tracks are all `Platform.OTHER`),
  and a few screens have no isolation-testable action (folder picker, debug
  display screens) — those get content-only tests.
