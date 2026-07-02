# `:cbox:common:strings:real`

> The production string implementation — `composeResources`-backed `StringProvider` and the single source of all string values.

The `:real` half of the string system. It holds the actual `StringProvider`
implementation, the string *values* themselves (XML under
`src/commonMain/composeResources/values/`), and an Android-only preview/Paparazzi
variant. The `composeResources` values here are the **single source of string
values for every platform**, replacing the old split of Android `R.string`, a
generated JVM map, and a hand-written CLI subset. Types and `CompositionLocal`
come from `:cbox:common:strings:api`.

## Contents

**Provider implementation** (`commonMain`)
- `ChipboxStringProvider.kt` — `class ChipboxStringProvider(strings: Map<SageStringId, String>) : StringProvider`. A synchronous, preloaded provider: `loadChipboxStrings()` (`suspend`, called once at startup) reads every `ChipboxStringId`'s text from `Res.allStringResources` into a map (keyed by the lowercased enum name), so non-composable view models resolve text without suspension. `formatChipboxString(...)` is a multiplatform stand-in for `String.format` over the Android-style `%1$s`/`%1$d`/`%s`/`%d` specifiers the strings use.

**Preview strings** (`androidMain`)
- `ChipboxPreviewStrings.kt` — `@Composable rememberChipboxStringProvider()`, the provider for `@Preview` / Paparazzi. Resolves via the `@Composable` `stringResource` API (no `runBlocking` preload). It installs a `ClasspathResourceReader` (reading the packaged `.cvr` files straight off the classpath) via `LocalResourceReader`, because under Paparazzi the Android resource reader's `Context` is unavailable. It probes the classpath once for a sentinel `.cvr`; when the resources aren't reachable (Android Studio Layoutlib previews) it falls back to using the id names as placeholder text. Running apps never reach here — they preload via `loadChipboxStrings()`.

**String value resources** (`commonMain/composeResources/values/`)
- 17 per-feature `strings-*.xml` files, the single source of every UI string value, with `<string name=...>` keys matching the lowercased `ChipboxStringId` enum names: `strings-accessibility.xml`, `strings-appui.xml`, `strings-component-library.xml`, `strings-crash-log.xml`, `strings-error-log.xml`, `strings-favorites.xml`, `strings-folder-picker.xml`, `strings-home.xml`, `strings-library.xml`, `strings-manage-library.xml`, `strings-now-playing.xml`, `strings-platform.xml`, `strings-playback-status.xml`, `strings-playlists.xml`, `strings-rescan-status.xml`, `strings-search.xml`, `strings-settings.xml`.

## Why depend on this module

Most modules should depend on `:cbox:common:strings:api` for the types and
accessors. Wire `:real` only where a concrete `StringProvider` is bound into the
Metro graph (the app, and the UI-test harness, which preloads the real provider
so screens render actual text). `:real` `api`-exposes `:api` transitively. Add a
new string by adding a `ChipboxStringId` entry in `:api` and a matching
`<string>` value here.

## How the values reach each platform

The Compose Multiplatform plugin (`compose.multiplatform`) does the
string-resource codegen: it generates a `Res` class (package
`net.sigmabeta.chipbox.common.strings.real.generated.resources`) and the
`Res.allStringResources` map that `loadChipboxStrings()` /
`rememberChipboxStringProvider()` index by key. AGP 9's KMP-library plugin ships
with Android resource processing off, so the build re-enables `androidResources`
to make the generated `.cvr` value resources reach the APK/AAR assets (otherwise a
runtime `MissingResourceException`). For Paparazzi, the build also publishes a
`composeResourcesElementsJar` (`compose-resources` classifier) exposing the
prepared `composeResources` in classpath shape, which the `:features:*:screenshot`
modules add to their test classpath so previews resolve real text.

## Using it

```kotlin
// Startup (app or test graph): preload once, then bind synchronously.
val provider: StringProvider = ChipboxStringProvider(loadChipboxStrings())

// Paparazzi / @Preview (Android):
@Preview
@Composable
fun MyScreenPreview() {
    CompositionLocalProvider(LocalChipboxStringProvider provides rememberChipboxStringProvider()) {
        MyScreen()
    }
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `sage.compose.kmp` + `compose.multiplatform`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (provider + XML values), `androidMain` (preview provider)
- **SAGE/module dependencies:** `api(projects.cbox.common.strings.api)`,
  `implementation(libs.sage.common.ui.strings)`,
  `implementation(libs.jetbrains.compose.resources)`
