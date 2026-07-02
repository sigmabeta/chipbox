# `:features:rescan-status:real`

> The live library-scan status screen — real-time progress plus a feed of added/updated/removed games.

The `:real` implementation of the rescan-status feature: a
`ChipboxListViewModel` that reduces the `Scanner`'s two flows (`state()` and
`scanEvents()`) into one batched list state, the `ListState` renderer, and the
Compose `Route` content. The app includes this module; navigation targets only
the `:api` route key.

## Contents

| File | What it is |
| --- | --- |
| `RescanStatusState.kt` | `RescanStatusState : ListState` plus `ScanPhase`, `ScanEventKind`, and `ScanEventItem`. Renders a Progress section (elapsed/games/tracks/failed, a live per-folder "Folder" row above a per-file "Scanning…" heartbeat row, and a failed-path row) followed by a reversed Changes feed of game-change rows. |
| `RescanStatusAction.kt` | `RescanStatusAction.GameClicked(gameId)` — tapping an added/updated game row. |
| `RescanStatusViewModel.kt` | `@ContributesIntoMap` / `@ViewModelKey` / `@Inject` ViewModel. Collects `Scanner.state()` + `Scanner.scanEvents()`, buffers them, and flushes to state on timers; handles `GameClicked` by emitting `NavigateTo(GameDetail(id))`. |
| `RescanStatusRoute.kt` | `@Composable RescanStatusRoute(onEvent, modifier)` — resolves the VM via `metroViewModel()` and renders it through `ChipboxListEntry`. |

## The live scan source

The screen observes the scanner directly, not a snapshot:

- **`Scanner.state()`** drives `phase` and the Progress counters
  (`timeInSeconds`, `gamesFound`, `tracksFound`, `tracksFailed`, `failedPath`),
  mapping `ScannerState.Scanning/Complete/Failed/Idle/Unknown` onto `ScanPhase`.
- **`Scanner.scanEvents()`** carries two distinct kinds of event, handled on
  separate cadences:
  - `ScannerEvent.FolderScanned` is a **per-folder heartbeat**. It updates only
    `currentFolder`, surfaced as a single live "Folder" row under Progress (the
    coarser "where" above the per-file row), and never enters the Changes list.
  - `ScannerEvent.FileScanned` is a **high-frequency per-file heartbeat**. It
    updates only `currentFile`, surfaced as a single live "Scanning…" row under
    Progress, and republished on a fast **500 ms** tick. It never enters the
    Changes list.
  - `GameFoundEvent` / `GameUpdated` / `GameRemoved` become `ScanEventItem`s
    appended to a pending buffer and flushed into the Changes list on a **1 s**
    batch tick. Batching is a deliberate perf mitigation: a large library fires
    thousands of events, and per-event recomposition would tank the screen.

`viewModelScope` is Main-confined, so the two collectors and the two tickers
mutate the buffer fields serially without synchronization. `publish()` reuses a
single `eventsSnapshot` instance so quiet/fast ticks produce an equal state and
get suppressed by the `StateFlow`.

## Why depend on this module

This is the includable screen half of the feature; the app graph picks up the
ViewModel binding from here. Don't depend on `:real` to merely navigate to the
screen — depend on `:features:rescan-status:api` for that and let the route map
in `ChipboxScreens.kt` reach this implementation. `:real` `api`-exposes `:api`.

## Using it

The screen is wired once in `cbox/common/appui/api/.../ChipboxScreens.kt`:

```kotlin
private object RescanStatusScreen : Screen {
    @Composable
    override fun Content() {
        RescanStatusRoute(onEvent = LocalChipboxEventSink.current)
    }
}

// screenFor(...) maps the route key to the screen:
RescanStatus -> RescanStatusScreen
```

The ViewModel is contributed to the app graph automatically via Metro
(`@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())` +
`@ViewModelKey`) and resolved inside `RescanStatusRoute` with `metroViewModel()`.

## Module facts

- **Plugin:** `chipbox.feature.real` + `chipbox.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (state, route, ViewModel) and `commonTest` (`RescanStatusViewModelTest`). The ViewModel + route live in `commonMain` because `scanner.api` is now KMP — no `jvmSharedMain` split.
- **SAGE/module dependencies:** `:features:rescan-status:api` (api), `:cbox:common:ui:list:api`, `:cbox:common:appcomm:api`, `:cbox:common:strings:api`, `:cbox:common:scanner:api`, `:features:game-detail:api`, `sage.common.images`; test: `:cbox:common:scanner:fake`.
