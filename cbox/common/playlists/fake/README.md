# `:cbox:common:playlists:fake`

> An in-memory `PlaylistsRepository` whose streams emit reactively.

The `:fake` test double for `:cbox:common:playlists:api`.
`FakePlaylistsRepository` holds every playlist (metadata + ordered track ids) in
one `MutableStateFlow`, so all the read streams emit reactively — enough to drive
the real Compose UI over fakes, and seedable up front.

## Contents

| File | What it is |
| --- | --- |
| `FakePlaylistsRepository.kt` | One `MutableStateFlow<List<Entry>>` plus monotonic id/clock counters for deterministic ids and timestamps. `seed(name, trackIds)` pre-populates a playlist; the full `PlaylistsRepository` surface (create/rename/delete, add/remove/reorder tracks, clear) mutates it in place. |

## Why depend on this module

Use it in UI/feature tests that drive the playlists list/detail screens or the
add-to-playlist CTAs without Room. The `:di` module also depends on it so the
debug menu can swap it in at runtime.

## Using it

```kotlin
val repo = FakePlaylistsRepository()
val id = repo.seed("Road trip", trackIds = listOf(1, 2, 3))
// repo.playlist(id) / repo.trackIds(id) now emit the seeded data
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** `:cbox:common:playlists:api`, `kotlinx-coroutines-core`
