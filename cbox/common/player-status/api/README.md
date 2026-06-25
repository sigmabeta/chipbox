# `:cbox:common:player-status:api`

> The mini-player: a `Director`-driven Compose bar plus its ViewModel and state.

The persistent mini-player surfaced at the bottom of the app — a self-contained
Compose component, its `StateFlow`-backed ViewModel, and the state/action types.
Despite the `:api` suffix this module ships real UI and a real ViewModel: it's an
`:api` only in that it exposes the public surface other shells compose into
(`ChipboxAppUi`) and contributes its ViewModel to `AppScope` via Metro
multibinding. It depends on `:cbox:common:player:director:api` and observes the
`Director`'s flows.

## Contents

- **Composables** — `PlayerStatus.kt`: the public `PlayerStatus()` entry point
  (resolves its VM via `metroViewModel()`, animates slide-in/out and artwork
  crossfade, renders the private `PlayerStatusCard`) plus the exported layout
  constants `PlayerStatusReservedHeight` and `PLAYER_STATUS_ANIM_DURATION_MS` for
  insetting host content. `PlayerStatusInfo.kt`: the internal title/caption block.
- **ViewModel** — `PlayerStatusViewModel.kt`: `@ContributesIntoMap(AppScope::class,
  binding = binding<ViewModel>())` + `@ViewModelKey` + `@Inject`. `combine`s
  `director.metadataState()` + `playbackState()` into a
  `StateFlow<PlayerStatusState>`; `sendAction` (an `ActionSink`) logs every intent
  and maps `PlayPauseClicked` to a `SessionRequest.Play`/`Pause`.
- **State / actions** — `PlayerStatusState.kt`: visible/isPlaying/isBuffering/
  isError + title/artistsCaption/artwork, with an `Empty` default.
  `PlayerStatusAction.kt`: `CardClicked` / `PlayPauseClicked` (`ChipboxAction`s).

Tests (`commonTest`): `PlayerStatusViewModelTest` drives the VM over `FakeDirector`.

## Why depend on this module

Depend on `:cbox:common:player-status:api` from the app shell that hosts the
mini-player (the shared `ChipboxAppUi`). Drop `PlayerStatus()` into the scaffold,
wire its `onClick` to navigate to Now Playing and `onVisibleChange` to inset
content by `PlayerStatusReservedHeight`. The ViewModel is provided by Metro — no
manual construction. It consumes the director through `:director:api`, so the app
must also wire `:director:di`.

## Using it

```kotlin
Scaffold { padding ->
    var reserve by remember { mutableStateOf(false) }
    Box(Modifier.padding(padding)) {
        content(if (reserve) PlayerStatusReservedHeight else 0.dp)
        PlayerStatus(
            modifier = Modifier.align(Alignment.BottomCenter),
            onVisibleChange = { reserve = it },
            onClick = { navigator.push(NowPlayingScreen) },
        )
    }
}
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `sage.compose.kmp` + `metro` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain` (pure Compose + `androidx.lifecycle` ViewModel; no `android.*` imports) / `commonTest`
- **SAGE/module dependencies:** `implementation` of `:cbox:common:ui:components:api`, `:cbox:common:models:api`, `:cbox:common:player:director:api`, `:cbox:common:appcomm:api`, plus `sage.common.appcomm`, `sage.common.di`, `sage.common.images`, `sage.common.logging`, `sage.common.ui.iconsReal`, `metrox.viewmodel(.compose)`. Test deps add `:director:fake`.
