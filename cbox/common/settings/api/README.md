# `:cbox:common:settings:api`

> User-facing app settings — the `ChipboxSettingsManager` interface and its enum values.

The `:api` module for Chipbox settings: it declares the `ChipboxSettingsManager`
interface (theme, fonts, resampler mode, and a few playback toggles) plus the
enums those settings range over. Pure types and contracts — depend on this to
read or write a setting; the app wires the production implementation via
`:cbox:common:settings:di`.

## Contents

| File | What it is |
| --- | --- |
| `ChipboxSettingsManager.kt` | The settings interface: `get*()` returns a `Flow` per setting, `set*()` writes it. Covers brand/plain font, theme mode, resampler mode, and the `shuffleSkipsShortTracks` / `volumeNormalizationEnabled` / `fadeInEnabled` booleans. |
| `ThemeMode.kt` | `LIGHT` / `DARK` / `SYSTEM` (default `SYSTEM`), with `fromStorageValue()` to parse a persisted name back. |
| `ResamplerMode.kt` | `OS` / `LINEAR` / `CUBIC` (default `CUBIC`) — how the player resamples emulator output to the device rate, with `fromStorageValue()`. |

## Why depend on this module

Depend on `:cbox:common:settings:api` whenever you need to read or change a user
setting — e.g. the appui theme reader (`ChipboxAppUiViewModel` lives in `appui`
commonMain, hence these types live in `commonMain`), or a settings screen's
ViewModel. It carries only `kotlinx-coroutines-core`, so it stays multiplatform.
The app injects `RealChipboxSettingsManager` (`:real`) via `:di`; tests inject
`FakeChipboxSettingsManager` (`:fake`).

## Using it

```kotlin
class ThemeReader(private val settings: ChipboxSettingsManager) {
    val theme: Flow<ThemeMode> = settings.getThemeMode()

    fun pickDark() = settings.setThemeMode(ThemeMode.DARK)
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf; `api`s `kotlinx-coroutines-core`)
