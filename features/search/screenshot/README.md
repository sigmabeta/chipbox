# `:features:search:screenshot`

> Paparazzi visual-regression tests for the search screen.

The `:screenshot` companion for `:features:search:real`: an Android-only Paparazzi
module that renders the search UI across a matrix of device configs and themes,
then diffs against committed golden images to catch unintended visual changes.
Because search renders its own custom screen rather than the standard
`ListScreenPreview` pipeline, the previews drive `SearchContent` directly with an
`ImmutableList<ListModel>` built from a `SearchState`.

## Contents

| File | What it is |
| --- | --- |
| `src/main/java/.../preview/Search.kt` | Two `@DevicePreviews @Composable`s — `Search` (a populated results state from `FakeModelGenerator`) and `SearchHistoryAndPrompt` (a recent-searches state) — each wrapping `SearchContent(...)` in `ScreenPreview`. |
| `src/test/java/.../preview/SearchScreenshots.kt` | `@RunWith(Parameterized::class)` Paparazzi test with `searchResults`/`searchHistory` snapshot tests, parameterized over `INTERESTING_DEVICES`. |
| `src/test/snapshots/images/` | The committed golden PNGs (git-LFS), one per preview × device × theme. |

## Why depend on this module

Nothing depends on this module — it's a test-only leaf that the CI screenshot job
builds. It depends on `:features:search:real` to render the real `SearchContent`,
and on SAGE `ui-components` because `SearchContent`'s signature exposes
`ImmutableList<ListModel>` (search doesn't go through `ListScreenPreview`). Add or
adjust previews here whenever the search UI changes so the goldens keep guarding
it.

## Using it

```sh
# Verify the search screen still matches its goldens (does NOT re-record):
./gradlew :features:search:screenshot:verifyPaparazziDebug

# Intentionally re-record goldens after a deliberate UI change (overwrites LFS):
./gradlew :features:search:screenshot:testDebugUnitTest
```

Prefer the repo's `scripts/paparazzi-diff.sh` over invoking the Gradle tasks
directly, and report the diff results back.

## Module facts

- **Plugin:** `chipbox.screenshot`
- **Targets:** Android only (Paparazzi runs on the JVM unit-test classpath).
- **Source set:** `src/main/java` (previews) + `src/test/java` (Paparazzi tests), with goldens in `src/test/snapshots/images`.
- **SAGE/module dependencies:** `:features:search:real`; SAGE `ui-components` (for the `ListModel` type in `SearchContent`'s signature).
