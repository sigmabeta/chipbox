# `:features:rescan-status:api`

> The navigable route key for the live library-scan status screen.

The public, KMP-safe entry point for the rescan-status feature: a single
`@Serializable` Voyager destination that any module can depend on to navigate
to the live scan screen without pulling in the screen implementation. This is
the `:api` half of the feature; the screen itself lives in `:real`.

## Contents

| File | What it is |
| --- | --- |
| `RescanStatus.kt` | `@Serializable data object RescanStatus` — the route key for the live scan-status screen. |

## Why depend on this module

Depend on `:features:rescan-status:api` when you need to *navigate* to the
rescan-status screen (e.g. emit `NavigateTo(RescanStatus)`) but don't want the
ViewModel, Compose content, or scanner wiring. The `:real` module supplies that
implementation; keeping the route key in `:api` lets callers reference the
destination without a dependency cycle.

## Using it

```kotlin
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.features.rescanstatus.RescanStatus

// From any screen's action handler:
emit(NavigateTo(RescanStatus))
```

The route is mapped to its Voyager `Screen` in
`cbox/common/appui/api/.../ChipboxScreens.kt` (`RescanStatus -> RescanStatusScreen`).

## Module facts

- **Plugin:** `chipbox.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf)
