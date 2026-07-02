# `:cbox:common:ui:components:api`

> The Chipbox Compose component library — the renderers SAGE's list framework draws,
> plus Chipbox-specific `ListModel` types.

The shared Compose Multiplatform composables Chipbox screens are built from, and the
glue that wires them into SAGE's generic list rendering. This is the `:api` (public
surface) module: feature screens depend on it for the renderers and Chipbox-only
`ListModel` types; there is no separate `:real`. It re-exports `libs.sage.common.ui.components`
(`api(...)`), so consumers get SAGE's `ListModel` base types and this module's
composables from one dependency.

## Contents

This is a large module (~90 files). Grouped by role:

- **`ListModel` → composable mapping** — `ComposableMapping.kt` is the keystone:
  `ListModel.Content(sink, debug, mod, pad)` is a big `when` over every `ListModel`
  subtype (SAGE's plus Chipbox's) that dispatches to the matching composable. It tags
  each row with its model's `simpleName` (for UI-test selection) and wraps rendering in
  SAGE's `WithMeasurementComponent` perf probe. This is how Chipbox `ListModel`s plug
  into SAGE's list rendering.

- **Chipbox-specific `ListModel`s + their composables** — types that live here rather
  than in SAGE because they're Chipbox-domain or would otherwise create dependency
  cycles: `NowPlayingHomeCardListModel` / `NowPlayingHomeCard` (Home-row variant of the
  mini-player) and `ScanStatusCardListModel` / `ScanStatusCard` (`ScanCardStatus`,
  `ScanStatusDetail` — a Home card mirroring the Rescan Status screen).

- **Shared list-item composables** — the renderers for SAGE's `ListModel`s:
  `IconNameListItem`, `NameCaptionListItem`, `NameCaptionValueListItem`,
  `ImageNameListItem`, `ImageNameCaptionListItem`, `LabelValueListItem`,
  `LabelRatingListItem`, `LabelCheckboxItem`, `ConfirmationListItem`, `ActionItem`,
  `EditTextListItem`, `ExpandingDropdownListItem`, `CollapsibleDetailsListItem`,
  `SearchHistoryListItem`, `NotifListItem`, `WideItem`, `Section*`/`Subsection*`
  headers, `EmptyListIndicator`, and the `Loading*` placeholder family.

- **Image components** — `HeroImage` (width-driven 3:4 cover), `GridImage`,
  `HorizontalScroller` (a `LazyRow` that recursively renders nested `ListModel`s).

- **Reorder / dismiss wrappers** — `DraggableListModel` / `DraggableListItem`,
  `DismissibleListModel` / `SwipeToRemoveBox` for reorderable and swipe-to-remove paths.

- **Drawing / effect sub-components (`subs/`)** — `CrossfadeImage` (the Coil-backed
  loader with size-bucketing, crossfade, and a fake/inspection branch), `FakeImage`
  (deterministic gradient stand-in), `ElevatedPill` / `ElevatedCircle` / `ElevatedRoundRect`,
  `Flasher` (loading shimmer), `Rating` (heart row), `LabeledThingy` / `LoadingThingy`,
  `MenuActionIcon`, plus the `LocalForceFakeImages` / `LocalInspectionMode` plumbing.

- **Layout / measurement utils (`utils/`)** — `FocusArea.kt` (`FocusAreaShape`,
  `outer`/`innerFocusPadding` for rounded focus highlights), `ImageSize.kt`
  (`THUMBNAIL`/`MEDIUM_HEIGHT`/`LARGE_HEIGHT` `Dp` buckets), `Math.kt` (`partialLerp*`,
  `Random.nextPercentageFloat`).

- **Preview / Paparazzi helpers (`androidMain`)** — `*Previews.kt` are Compose
  `@Preview` / Paparazzi-render harnesses (one per component), plus `previews/`
  scaffolding (`ChipboxPreview`/`ChipboxTheme`, `PreviewActionSink`, `Fullscreen`,
  sizing constants). `commonMain/previews/` holds the shared preview constants.

### `expect`/`actual` across targets

Three declarations are platform-split so the gradient/inspection and debug-overlay
behaviour resolves per target:

- `subs/FakeImage` — `commonMain` `expect`; `androidMain` reuses the historical
  `android.graphics` `BitmapGenerator` (keeps Paparazzi goldens byte-identical),
  `jvmMain`/`jsMain` draw an equivalent Compose-graphics gradient.
- `subs/ImageAnimationsEnabled` (`crossfadeImagesEnabled`) — `expect` in `commonMain`,
  `actual` `true` on JVM/Android (`src/main/java`, `jvmShared`) and `false` on `jsMain`
  (Long-emulation cost during grid scroll).
- `StackFrameSummary` (`Throwable.firstFrameSummary()`) — `commonMain` `expect`;
  JVM/Android read the `StackTraceElement`, JS falls back to the first stack line.

## Why depend on this module

Depend on `:cbox:common:ui:components:api` from any feature/shared UI module that needs
to render `ListModel`-driven lists or reuse the Chipbox component look. It is the single
import that brings both SAGE's `ListModel` types (re-exported) and Chipbox's renderers
+ extra models. The Chipbox-only `ListModel`s (`NowPlayingHomeCardListModel`,
`ScanStatusCardListModel`) and their `ComposableMapping` wiring live here so a screen's
state can emit them and have them render automatically.

## Using it

Build a list of `ListModel`s in your state, then render each with `Content`:

```kotlin
@Composable
fun HomeList(models: List<ListModel>, sink: ActionSink) {
    LazyColumn {
        items(models, key = { it.dataId }) { model ->
            model.Content(
                sink = sink,
                debug = false,
                mod = Modifier.animateItem(),
                pad = PaddingValues(horizontal = 16.dp),
            )
        }
    }
}

// A Chipbox-specific model dispatches to its composable through the same path:
val card = ScanStatusCardListModel(
    status = ScanCardStatus.SCANNING,
    statusLabel = "Scanning…",
    currentFile = "/music/track.spc",
    detail = ScanStatusDetail.Rows(rows),
)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `sage.compose.kmp`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (composables + models), `src/main/java` (`jvmSharedMain`
  actuals), `androidMain`/`jvmMain`/`jsMain` (platform `actual`s + Android preview helpers)
- **SAGE/module dependencies:** `libs.sage.common.ui.components` (`api`),
  `sage.common.appcomm`, `sage.common.images`, `sage.common.ui.perfCompose`,
  `sage.common.ui.iconsReal`, Coil (`coil.kt.core`/`coil.kt.compose`); project deps
  `:cbox:common:strings:api` and `:cbox:common:ui:fonts:api`; `androidMain` adds
  `:cbox:android:ui:theme:api`, `:cbox:common:strings:real`, `:cbox:android:images:api`,
  and Compose tooling.
