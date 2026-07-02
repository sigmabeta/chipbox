# `:cbox:common:ui:freeform:api`

> Scaffolding for non-list ("freeform") screens — a VM base and Compose entry that hand a rendered model straight to a caller-supplied layout.

The `:api` module for screens that don't decompose into a list of `ListModel`s
but instead draw their own Compose layout. It mirrors `:cbox:common:ui:list:api`
but is built over SAGE's freeform system (`sage.common.freeform`:
`FreeformState`/`FreeformStateActual`) instead of the list pipeline. The canonical
consumer is Now Playing. Being `:api`, it is the shared base feature `:real`
modules extend; there is no `:real` impl here.

## Contents

| File | What it is |
| --- | --- |
| `ChipboxFreeformViewModel.kt` | `abstract class ChipboxFreeformViewModel<S : FreeformState<Model>, Model> : ViewModel(), ActionSink`. Sibling of `ChipboxListViewModel`; the difference is that `uiStateActual` is typed `FreeformStateActual<Model>`, so the entry can hand the rendered `Model` directly to the screen. Holds `state: StateFlow<S>`, derives `uiStateActual` via `S.toActual(stringProvider)`, exposes `showDebug` and a one-shot `events: Flow<ChipboxEvent>`. Notably, `events` is a **buffered `Channel`** (not a `replay=0 SharedFlow`) so an event emitted before the UI starts collecting — e.g. Now Playing firing `NavigateBack` the instant it sees an idle Director — isn't dropped. Subclasses use `protected updateState`, `protected emit`, and the `final sendAction` → `abstract handleAction(SageAction)` funnel. |
| `ChipboxFreeformEntry.kt` | `@Composable fun <Model> ChipboxFreeformEntry(viewModel, onEvent, modifier, content)` — forwards `events` to `onEvent`, runs `ScreenLifecycleEffect` (reused from `:cbox:common:ui:list:api`), sets the title bar from `state.title`, then invokes the `content` slot with `(model, actionSink, showDebug, modifier)`. The caller supplies the actual Compose layout; this entry only does the plumbing. |

## Why depend on this module

Depend on `:cbox:common:ui:freeform:api` when a feature screen needs a bespoke
Compose layout rather than a scrolling list/grid. Extend
`ChipboxFreeformViewModel<S, Model>` in the feature `:real` and render it through
`ChipboxFreeformEntry`, passing your layout as the `content` lambda. For
list-shaped screens use `:cbox:common:ui:list:api` instead.

## Using it

```kotlin
// :real — the Route, supplying its own layout via the content slot
@Composable
fun NowPlayingRoute(onEvent: (ChipboxEvent) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: NowPlayingViewModel = metroViewModel()
    ChipboxFreeformEntry(viewModel, onEvent, modifier) { model, actionSink, showDebug, mod ->
        NowPlayingContent(model, actionSink, showDebug, mod)
    }
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `sage.compose.kmp`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** SAGE `common.freeform`, `common.appcomm`, `common.ui.strings`, `common.ui.components` (api). Project: `:cbox:common:appcomm:api`, `:cbox:common:ui:list:api` (api — reuses `ScreenLifecycleEffect`), `:cbox:common:ui:chrome:api` (impl).
