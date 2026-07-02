# `:cbox:common:models:api`

> The domain models — `@Serializable` `Artist`/`Game`/`Track`/… types features and the repository speak in.

Holds Chipbox's domain models: the hydrated, in-memory types that features consume and
the library `Repository` serves. This is an `:api` module of pure types. Every model is
`@Serializable` (kotlinx.serialization) so the same types cross the HTTP boundary
between `apps/server` and `apps/js` (`RemoteRepository`) without DTO duplication. These
are the domain counterpart to the Room rows in `:cbox:common:entities:api` — entities are
DB rows the DAOs operate on; models are the domain types; the repository
(`:cbox:common:repository:real`) maps between them.

## Contents

| File | What it is |
| --- | --- |
| `Track.kt` | `@Serializable` track model (nullable `game`/`artists` back-links, `gameId`, `chainFiles: List<ChainFile>`, `platform`, descriptive-tag fields). Also defines `const val FADE_LENGTH_MS = 6_000L`. |
| `Game.kt` | `@Serializable` game model with nullable `artists`/`tracks` back-links and optional release-level metadata. |
| `Artist.kt` | `@Serializable` artist model with nullable `tracks`/`games` back-links. |
| `Playlist.kt` | `@Serializable` playlist metadata; `trackCount` is denormalized for the list screen (members hydrated on demand). |
| `Platform.kt` | `@Serializable` enum (`NES`, `SNES`, `PSX`, …`OTHER`) carrying a `SageStringId` for display; serializes by name. |
| `SearchHistory.kt` | `@Serializable` recent-search entry (`id`, `query`). |
| `ChainFile.kt` | `@Serializable` `(filename, uri)` pair **plus** the `encodeChainFiles`/`decodeChainFiles` codec (tab-delimited, newline-separated) used to flatten a track's chain files into `TrackEntity.chainFiles`. |

Tests (`commonTest`): `ModelSerializationTest` round-trips every wire type through
`Json` (the `Platform` enum-by-name and nullable back-links), and `ChainFileCodecTest`
covers the `ChainFile` codec's round-trips and malformed-line handling.

## Why depend on this module

Depend on `:cbox:common:models:api` whenever you need the domain types — feature
screens, view models, and any module that serves or transports them. The repository
(`:cbox:common:repository:real`) converts these models to/from the Room entities in
`:cbox:common:entities:api`; consumers should not reach for the entities directly.
`api(projects.cbox.common.strings.api)` is exposed transitively for `Platform`'s
`ChipboxStringId`, as is `kotlinx-serialization-core` so importers can resolve the
generated serializers.

## Using it

Models are plain data classes; back-links are nullable so a partially-hydrated graph
(e.g. one served over HTTP) is valid. `ChainFile` adds a codec for the entity boundary:

```kotlin
val track = Track(
    id = 42L,
    path = "/library/Mega Man 2/01 Title.nsf",
    source = "local",
    title = "Title Theme",
    trackLengthMs = 90_000L,
    trackNumber = 1,
    fadeLengthMs = FADE_LENGTH_MS,
    game = null,        // back-links may be left unhydrated
    artists = null,
    platform = Platform.NES,
)

// Flatten chain files for storage in TrackEntity.chainFiles, and read them back:
val encoded = encodeChainFiles(track.chainFiles)
val files: List<ChainFile> = decodeChainFiles(encoded)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `kotlin.serialization`
- **Targets:** Android + JVM; JS (Node) when built with `-Psage.js`
- **Source set:** `commonMain` (tests in `commonTest`)
- **SAGE/module dependencies:** `api(projects.cbox.common.strings.api)` (for
  `Platform`'s string ids); `api(libs.kotlinx.serialization.core)`.
