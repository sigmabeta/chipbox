# `:cbox:common:appcomm:api`

> Chipbox's app-wide action/event vocabulary — the `ChipboxAction` and `ChipboxEvent` types.

The `:api` module for app-wide communication: it specializes SAGE's app-comm
layer for Chipbox. `ChipboxAction` extends SAGE's `SageAction` (the inbound
vocabulary a screen's reducer consumes — init/resume/back/search/reorder/etc.),
and `ChipboxEvent` is the outbound vocabulary a ViewModel emits for the host shell
to act on (navigation, snackbars, clipboard, chrome visibility). Pure types — no
`:real`/`:di`/`:fake`.

## Contents

| File | What it is |
| --- | --- |
| `ChipboxAction.kt` | `open class ChipboxAction : SageAction()` — the Chipbox-side base for inbound actions, inheriting SAGE's full `SageAction` set so feature reducers can subclass with their own actions. |
| `ChipboxEvent.kt` | `sealed class` of outbound one-shot events: `NavigateTo`/`NavigateBack`, `OpenUrl`, `ShowSnackbar`, `PickFolder`, `CopyToClipboard`, and the host-chrome *requests* `RequestMiniPlayerVisibility` / `RequestTopBarVisibility` (requests, not commands — the collector decides whether to honour them). |

## Why depend on this module

Depend on `:cbox:common:appcomm:api` from any feature ViewModel that needs to emit
host-level events (navigate, snackbar, toggle chrome) or that subclasses
`ChipboxAction`, and from the app shell that collects `ChipboxEvent`. It re-exports
`sage.common.appcomm`, so consumers get `SageAction` too.

## Using it

```kotlin
// ViewModel emits an outbound event:
events.emit(ChipboxEvent.ShowSnackbar("Added to playlist"))
events.emit(ChipboxEvent.RequestTopBarVisibility(visible = false))

// the host shell collects and reacts:
when (event) {
    is ChipboxEvent.NavigateBack -> navigator.pop()
    is ChipboxEvent.OpenUrl -> openInBrowser(event.url)
    else -> { /* ... */ }
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `sage.common.appcomm` (api) — leaf otherwise
