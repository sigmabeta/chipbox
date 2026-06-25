# `:cbox:common:strings:api`

> The string-resource API: a `ChipboxStringId` key enum and a `CompositionLocal` for resolving it to text.

The public surface of Chipbox's multiplatform string system. It declares the
typed id for every UI string (`ChipboxStringId`) and the Compose accessors that
turn an id into displayable text — over SAGE's `ui.strings` (`SageStringId` /
`StringProvider`). This is the `:api` module: it carries the types and the
`CompositionLocal` only; the `composeResources`-backed implementation lives in
`:cbox:common:strings:real`.

## Contents

| File | What it is |
| --- | --- |
| `ChipboxStringId.kt` | `enum class ChipboxStringId : SageStringId` — one entry per UI string, grouped by feature (accessibility, home, search, library, playlists, settings, now-playing, platform names, playback-status, rescan-status, …). The enum *name* (lowercased) is the resource key in `:real`'s XML. |
| `LocalChipboxStringProvider.kt` | `val LocalChipboxStringProvider = staticCompositionLocalOf<StringProvider>` plus the `@Composable`/`@ReadOnlyComposable` extension accessors `ChipboxStringId.text()`, `text(arg)`, `textInt(arg)`, and `text(first, second)`. |

## Why depend on this module

Depend on `:cbox:common:strings:api` from any feature/UI module that needs to
display a string — it is the multiplatform replacement for Android's
`stringResource` + `R.string`, so the same code resolves text on Android, JVM,
and JS. You reference a `ChipboxStringId` and call `.text()`; you never touch
`R`. The app (`MainActivity` / `DesktopMain`) wires the Metro-graph
`StringProvider` (a `ChipboxStringProvider` from `:real`) into
`LocalChipboxStringProvider` once near the Compose root; everything below reads
through it.

## Using it

```kotlin
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.api.text
import net.sigmabeta.chipbox.strings.api.textInt

@Composable
fun Header(trackCount: Int) {
    Text(ChipboxStringId.SEARCH_SCREEN_TITLE.text())
    Text(ChipboxStringId.PLAYLISTS_TRACK_COUNT.textInt(trackCount))
}
```

Provide it once at the root (done by the app shell):

```kotlin
CompositionLocalProvider(LocalChipboxStringProvider provides stringProvider) {
    // app content
}
```

Non-composable code (e.g. view models) resolves through the injected
`StringProvider` directly (`getString(id)` etc.).

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `sage.compose.kmp`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `api(libs.sage.common.ui.strings)` (provides
  `SageStringId` / `StringProvider`)
