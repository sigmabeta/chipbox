# `:cbox:common:webroutes`

> Pure route ↔ URL mapping for the browser client — the path table the web shell mirrors into
> `window.history`, plus the shared `allowsNavigation` capability policy.

`apps/js` is a browser client with path-based URLs (ADR-0003): `/game/42`, `/library/favorites`,
`/settings/errors`. This module owns the rules that turn a feature route key into that path and
back. It is pure `commonMain` — no Compose, no Voyager, no `kotlinx.browser`, no DOM — so the same
rules run as Node tests (`jsNodeTest`) and JVM tests (`jvmTest`) without a browser.

It also hosts `AppCapabilities.allowsNavigation(destination)`, the single capability policy the
in-app event sinks **and** the URL decoder consult. The decoder must never publish a deep link to a
surface the shell refuses, so the policy lives beside the table instead of being duplicated.

## URL table

| URL | Target |
| --- | --- |
| `/` | `ChipboxTarget.Tab(HOME)` |
| `/library` | `ChipboxTarget.Tab(LIBRARY)` |
| `/search` | `ChipboxTarget.Tab(SEARCH)` |
| `/settings` | `Deep(Settings)` |
| `/settings/errors` | `Deep(ErrorLog)` — only when `showErrorLog` |
| `/library/favorites` | `Deep(Favorites)` |
| `/library/playlists` | `Deep(Playlists())` |
| `/library/playlists/:id` | `Deep(PlaylistDetail(id))` |
| `/library/games` | `Deep(BrowseByGame)` |
| `/library/artists` | `Deep(BrowseByArtist)` |
| `/library/platforms` | `Deep(BrowseByPlatform)` |
| `/library/tracks` | `Deep(BrowseAllTracks)` |
| `/game/:id` | `Deep(GameDetail(id))` |
| `/artist/:id` | `Deep(ArtistDetail(id))` |
| `/artist/:id/tracks?name=…` | `Deep(TracksForArtist(id, name))` |
| `/platform/:enum/games` | `Deep(GamesForPlatform(platform))` |
| `/now-playing` | `Deep(NowPlaying)` |

Rules:

- **Ids are positive decimal longs.** `0`, negative, non-numeric, and overflowing ids are unknown.
- **Platform names are case-insensitive enum names** (`snes`, `SNES`, `gameboy_advance`).
- **Artist names are UTF-8 percent-encoded.** A missing `name` decodes as an empty title; the
  detail screen refetches tracks by id, so the name is cosmetic.
- **Trailing slashes are canonicalised** (`/game/42/` → `GameDetail(42)`); a fragment is ignored.
- **`?api=` and unknown query parameters are ignored.** `?api=` remains the dev server's base-URL
  override (`RemoteRepository.resolveBaseUrl`); the history host re-appends it to every URL it
  writes so a refresh keeps the override.
- **Unknown paths have no target** (`decode` returns null); the host falls back to `/`.
- **Admin and debug-only destinations have no URL.** Manage Library, Folder Picker, Rescan Status,
  Playback Status, Crash Log, and Component Library are hidden on web (ticket 05); `encode` returns
  null and no path decodes to them. The Error Log is the deliberate exception (browser-local,
  reachable under Settings → Diagnostics) and is gated by `showErrorLog`.
- **Picker-mode `Playlists` is never encoded.** Its `pendingTrackIds`/`suggestedName` are transient
  bulk-add arguments and its Voyager key is hashed on them; only the normal empty list is a URL.
- **Tab-root destinations canonicalise to their tab.** `Deep(Home)` encodes `/`, and decoding it
  yields `Tab(HOME)` — a tab root is a tab, not a pushed screen.
- **Absolute URLs are accepted** by `decode` (`https://host/game/42`), which is convenient for tests
  and for pasted links; the host itself passes `pathname + search`.

## Using it

```kotlin
val url = RouteUrl.encode(ChipboxTarget.Deep(GameDetail(42)))       // "/game/42"
val target = RouteUrl.decode("/game/42", capabilities)              // ChipboxTarget.Deep(GameDetail(42))
val fallback = RouteUrl.decode("/nope", capabilities) ?: ChipboxTarget.Tab(ChipboxTab.HOME)
```

`apps/js`'s `BrowserHistoryNavigation` is the only production caller. It translates between these
targets and the shell's `BrowserNavigation` seam (`pushState` / `replaceState` / `popstate`).

## Contents

| File | What it is |
| --- | --- |
| `RouteUrl.kt` | `ChipboxTab`, `ChipboxTarget` (`Tab` / `Deep`), and `RouteUrl.encode` / `RouteUrl.decode` plus the path/query/percent codecs. |
| `NavigationPolicy.kt` | `AppCapabilities.allowsNavigation(destination)` — the shared shell/decoder policy. |

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `chipbox.plugins.kmp.test`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (codec) / `commonTest` (Node + JVM tests)
- **Dependencies:** `:cbox:common:capabilities:api`, `:cbox:common:models:api`, and the feature
  `:api` modules whose route keys appear in the table (home, library, search, settings, error-log,
  favorites, playlists, playlist-detail, browse-by-*, game/artist-detail, games-for-platform,
  tracks-for-artist, now-playing). Admin/debug `:api` modules are present only so `encode` can
  refuse their keys explicitly.
- **Do not apply `sage.compose.kmp`:** Compose's JS distribution ships Skiko only for browsers/WASM,
  so a Node test would fail at import. That is why the codec is a standalone module instead of
  living in `cbox/common/appui/api` (whose JS tests are disabled).

## Tests

```sh
./gradlew :cbox:common:webroutes:jsNodeTest :cbox:common:webroutes:jvmTest \
  -Psage.js=true -Dorg.gradle.isolated-projects=false \
  -Dorg.gradle.configureondemand=false --no-parallel
```

Coverage: every encodable destination round-trips; tab roots; enum case-insensitivity; bad,
negative, zero, and overflowing ids; picker-mode Playlists and `PickFolder`/admin/debug refusals;
the `showErrorLog` gate; unknown paths/trailing slashes/fragments/absolute URLs; UTF-8 artist-name
percent encoding (spaces, `+`, `%`, CJK, quotes, `/?&=`); and `?api=` being ignored as a route
argument.
