# `:cbox:common:ui:components:api`

> Chipbox's plug-in into SAGE's shared renderer suite, plus the Chipbox-owned `ListModel` rows.

The built-in `ListModel` renderer suite now lives in SAGE
([`:common:ui:composables`](../../../../../sage/common/ui/composables/README.md), package
`net.sigmabeta.sage.ui.composables`). This module no longer holds a copy of it; it keeps only the
Chipbox-specific rows and the glue that plugs them into SAGE's `ListModel.Content` entry point.

## Contents

- **Chipbox-owned rows** — `NowPlayingHomeCardListModel` / `NowPlayingHomeCard` (the Home-row
  variant of the mini-player) and `ScanStatusCardListModel` / `ScanStatusCard` (`ScanCardStatus`,
  `ScanStatusDetail` — the Home card mirroring the Rescan Status screen). These `ListModel`
  subclasses live here rather than in SAGE because they're Chipbox-domain.
- **`ChipboxRenderers`** — a `SageRenderers` whose `contentFor` maps the two Chipbox rows to their
  cards (and tags them with their model `simpleName`, the way the old `ComposableMapping` did) and
  whose `previewImage` plugs Chipbox's Android `BitmapGenerator` art back into SAGE's
  inspection-mode stand-in. SAGE's `ListModel.Content` consults it before its built-in renderers;
  every other model falls through to SAGE.
- **`ProvideChipboxText(stringProvider) { }`** — installs both locals the renderers read, in one
  place: `LocalChipboxStringProvider` (chipbox's own) and SAGE's `LocalSageStringProvider` +
  `LocalSageRenderers` (via `ProvideSageRenderers`, wired to `ChipboxRenderers`). Every composition
  root that used to install `LocalChipboxStringProvider` alone (Android, desktop, web, the Paparazzi
  `ScreenPreview`s, the UI-test harnesses) calls this instead.
- **`chipboxPreviewImage`** — an `expect val` of type `(@Composable (SourceInfo, Modifier) -> Unit)?`
  with an Android `actual` that reuses `BitmapGenerator` (keeps the recorded Paparazzi goldens
  byte-identical) and JVM/JS `actual`s that are `null` (SAGE's default gradient).

## Why depend on this module

Depend on `:cbox:common:ui:components:api` from any module that needs the Chipbox `ListModel` rows
(`NowPlayingHomeCardListModel`, `ScanStatusCardListModel`) or the shared
`ProvideChipboxText`/`ChipboxRenderers` wiring. It re-exports `libs.sage.common.ui.composables`
(`api(...)`), so consumers get SAGE's `ListModel` vocabulary, the shared renderer entry points
(`ListModel.Content`, `SageRenderers`, `CrossfadeText`, `CrossfadeImage`, …), and this module's
Chipbox-only surface from one dependency.

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `sage.compose.kmp`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source sets:** `commonMain` (rows + renderers + provider helper), `androidMain`/`jvmMain`/`jsMain`
  (the `chipboxPreviewImage` actuals)
- **SAGE/module dependencies:** `libs.sage.common.ui.composables` (`api`),
  `libs.sage.common.ui.iconsReal`; project deps `:cbox:common:strings:api`;
  `androidMain` adds `:cbox:android:images:api` (`BitmapGenerator`).
