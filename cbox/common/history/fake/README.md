# `:cbox:common:history:fake`

> A recording, seedable `PlaybackHistoryRepository` for tests.

The `:fake` test double for `:cbox:common:history:api`.
`FakePlaybackHistoryRepository` records every `recordPlay` and lets tests seed
the read streams, so recorder tests can assert which plays landed and feature
tests can stand up a VM without Room.

## Contents

| File | What it is |
| --- | --- |
| `FakePlaybackHistoryRepository.kt` | `recordedPlays` holds every recorded `Track` in order; `clearCalls` counts `clearHistory`. Read streams return the seeded `recent` / `mostPlayedSongs` / `mostPlayedGames` / `mostPlayedArtists`, each capped at the requested limit. |

## Why depend on this module

Use it in `:cbox:common:history:real`'s recorder tests (assert which plays got
recorded) and in feature tests that build a VM over the repository. The `:di`
module also depends on it so the debug menu can swap it in at runtime.

## Using it

```kotlin
val repo = FakePlaybackHistoryRepository().apply {
    recent = listOf(RecentPlay(trackId = 1, timeMs = 100))
}
recorder.observe()
// ... drive the director past the threshold ...
assertEquals(listOf(1L), repo.recordedPlays.map { it.id })
```

## Module facts

- **Plugin:** `sage.kmp`
- **Targets:** Android + JVM
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:history:api`, `:cbox:common:models:api`, `kotlinx-coroutines-core`
