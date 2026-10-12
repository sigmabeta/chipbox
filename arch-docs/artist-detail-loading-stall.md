# Composer (artist) detail — loading stall + missing loading state

Status: **fixed and verified on an Android emulator.** The stall was a genuine
Main-thread freeze, **not** the N+1 the original handoff suspected. Both the
render freeze and the (secondary) N+1 are addressed.

Reported symptom (verbatim intent): *"clicking on a composer with hundreds of
songs does not show a loading state, and stalls the UI before the ArtistDetail
screen shows."*

## What was verified (do not re-investigate)

The loading state **is** correctly wired end-to-end and is covered by a test.
The "no loading state" part of the report was a perception of the freeze, not a
data-flow bug:

1. `setupFlowWithId` emits `onStart { emit(Data.Loading) }` and the whole chain
   (including `onStart`) is moved to `ioDispatcher` via `flowOn`.
   `cbox/common/repository/real/.../DatabaseRepository.kt`.
2. `ArtistDetailViewModel.init` collects that flow on `viewModelScope` (Main) and
   fans `Data.Loading` to `LCE.Loading` on all three slots.
   `features/artist-detail/real/.../ArtistDetailViewModel.kt`.
3. `LCE.Loading` renders as shimmer placeholder items; the screen renders through
   `GridScreen` with `ColumnType.Staggered`.
   `features/artist-detail/real/.../ArtistDetailState.kt`;
   `sage/common/list/.../ListState.kt`;
   `sage/common/ui/list-screens/.../GridScreen.kt`.
4. A test asserts the loading fan-out:
   `features/artist-detail/real/src/commonTest/.../ArtistDetailViewModelTest.kt`.
5. The screenshot suite has a dedicated loading preview:
   `features/artist-detail/screenshot/.../ArtistDetail.kt`.

## Root cause (traced): a non-lazy section composing every row on Main

`GridScreen` (the `Staggered` path) is itself lazy, but `ArtistDetailState`'s
songs section is a **single `SectionListModel`** (header + one row per track),
and a section is rendered by a plain, non-lazy `Column`
(`cbox/common/ui/components/api/.../SectionListItem.kt` —
`model.sectionItems.forEach { it.Content(...) }`). A non-lazy section is
deliberate: it lets the whole section move between grid columns as one
contiguous unit across screen sizes. It works for GameDetail (bounded track
counts) but a prolific composer's hundreds of rows all compose in one pass.

### Emulator evidence

Artist **"Nobuo Uematsu" (id 433) = 721 tracks across 16 games**.

- Main thread is pegged ~95–100% CPU for **~3.4 s** — ANR-level.
- IO / Room threads are effectively **idle** in the same window (~44 ms total);
  `HeapTaskDaemon` burned ~600 ms in GC (allocation churn, not DB). So the stall
  was **not** IO-bound.
- `Choreographer#doFrame` → `Recomposer:recompose` opened and sat for **~2.5 s**
  before the next frame marker — one giant recomposition.
- The N+1 the original handoff targeted (per-track `getGameSync`) is real but
  minor here: Uematsu spans only 16 distinct games, so the 128-entry
  `gameByIdCache` absorbs it. It would only bite composers spanning >128 games.

(Reproduce with `adb shell atrace --async_start -b 65536 -a
net.sigmabeta.chipbox.debug view sched input am wm`, tap the artist, stop, then
aggregate main-thread on-CPU from `sched_switch` and frame-work spans from the
`tracing_mark_write` markers.)

## Fix implemented

1. **Bound the inline section, add a lazy full list.**
   - `ArtistDetailState.songSection` now renders at most `SONGS_PREVIEW_COUNT`
     (50) rows and appends an `ArtistDetailAction.ViewAllSongsClicked` CTA
     ("View all N songs") when there are more.
   - New feature `:features:tracks-for-artist` (api/real/screenshot): an
     `ArtistDetail`-adjacent screen rendering the full list as a plain lazy
     `ColumnType.One` `ListScreen`, so length is unbounded. Reached via the route
     key `TracksForArtist(artistId, artistName)`, wired in `ChipboxScreens.kt`.
     Play/shuffle/tap start `ARTIST` sessions (same as artist-detail).
   - The section stays one contiguous, column-movable unit; only its size is
     bounded. The full list is one tap away.

2. **Batcher for the N+1 (the original handoff recommendation).**
   - `GameDao.getGamesByIdsSync(ids): List<GameEntity>` (new suspend sibling of
     `getGamesByIds`), mirrored in `FakeDatabase`.
   - `DatabaseRepository.cachedTracksForArtist` now resolves every track's game
     in **one** query instead of a `getGameSync` round-trip per row, guarded by a
     `traceAsync("Repository:cachedTracksForArtist")` span. Order is preserved
     from the DAO's `ORDER BY`.

### Verification after the fix

- Main thread's longest uninterrupted block during the artist load dropped from
  **~2.5 s to ~12 ms** (frame-sized); it now renders in ~1.4 s of back-to-back
  frames instead of a single freeze.
- The "View all 721 songs" CTA renders; tapping it opens the lazy screen, which
  loads in ~0.8 s and stays virtualized while scrolling.
- Unit tests: `ArtistDetailViewModelTest` (incl. a new `ViewAllSongsClicked`
  navigation test), `TracksForArtistViewModelTest`, and
  `DatabaseRepositoryArtistTracksTest` (asserts one batched game query, zero
  per-row `getGameSync`). `ktlint` + `detekt` clean.
- Screenshots: 24 new goldens for `:features:tracks-for-artist:screenshot`;
  artist-detail goldens unchanged (its preview has <50 tracks, so no CTA).

## Out of scope

- A dedicated paging experience for the full song list. The lazy list already
  handles thousands of rows; paging is a feature, not a perf fix.
- Changing `DEFAULT_GAME_CACHE_SIZE` — moot now that the artist path batches.
- The `js`/`RemoteRepository` path — the N+1 was a Room concern.
