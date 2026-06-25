# `:cbox:common:coverart:api`

> The pure cover-art domain: IGDB lookup outcomes, results, keys, and credentials.

The shared, I/O-free contract for Chipbox's cover-art subsystem — the IGDB lookup
outcome (`CoverLookup`), the per-game fetch result + run summary, the cache key
and freshness window, IGDB credentials parsing, and the manual-override record.
This is the `:api` module: depend on it to consume these types. The production
implementations (cache, IGDB client, HTTP, file I/O) live in
[`:cbox:common:coverart:real`](../real). Everything here is `commonMain` with no
`java.*` and no I/O, so Android, the desktop JVM app, and the CLI build against
the same contract.

## Contents

| File | What it is |
| --- | --- |
| `CoverLookup.kt` | Sealed `CoverLookup` outcome of one IGDB lookup: `Found(imageId, …)`, `NoCover`, `NoMatch` (the first two are `Matched`, carrying `igdbId`/`igdbName`/`igdbSlug`). Also `IgdbGameInfo`, a game resolved by id/slug. |
| `CoverArtResult.kt` | `CoverArtOutcome` enum (`DOWNLOADED`, `UP_TO_DATE`, `NO_MATCH`, `NO_COVER`, `NO_FOLDER`, `FAILED`), the per-game `CoverArtResult`, and the aggregate `CoverArtSummary` with a derived `total`. |
| `CoverArtKey.kt` | `coverArtKey(title, platforms)` — the shared cache/override key (title + sorted platform names), plus `COVER_ART_TTL_DAYS` (180), the freshness window for cached matches. |
| `IgdbCredentials.kt` | `IgdbCredentials(clientId, clientSecret)` and `parseIgdbCredentials(lines)`, which parses the `[igdb]` INI section (pure; disk reads are the caller's job). |
| `OverrideEntry.kt` | `@Serializable OverrideEntry` — a manual pin from a Chipbox game to a specific IGDB game's cover (`igdbId`, `igdbName`, `imageId`, `igdbSlug?`). |

Tests (`commonTest`): `CoverArtKeyTest` locks the key's contract — same inputs
produce the same key, platform-set order is irrelevant, different titles/platforms
don't collide, and an empty platform set yields a deterministic `"Game|"`.

## Why depend on this module

Depend on `:cbox:common:coverart:api` to refer to cover-art lookup outcomes,
results, keys, or credentials without pulling in any HTTP/file machinery — for
example a UI surfacing override state, or a CLI printing a `CoverArtSummary`. The
app wires `:cbox:common:coverart:real` for the implementations; this `:api` module
is the type-level seam between them. There is no `:coverart:di` module — `:real`
depends directly on this `:api`.

## Using it

```kotlin
// Build the cache/override key for a game, then branch on a lookup outcome.
val key = coverArtKey(game.title, platforms)

when (val lookup: CoverLookup = resolve(game.title, platforms)) {
    is CoverLookup.Found -> useCover(lookup.imageId)        // size-independent cover id
    is CoverLookup.NoCover -> markMatchedButCoverless(lookup.igdbName)
    CoverLookup.NoMatch -> markUnmatched()
}

// Credentials come from a tiny [igdb] INI section (parsing only; reading is the caller's).
val creds: IgdbCredentials? = parseIgdbCredentials(configLines)
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `kotlin.serialization`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js` (the JS
  target has no `jvmSharedMain`, so it forces every file through `commonMain` and
  verifies the layer is genuinely pure)
- **Source set:** `commonMain` (no `jvmSharedMain`)
- **SAGE/module dependencies:** `:cbox:common:models:api` (for `Platform`),
  `kotlinx-serialization-core` (the `@Serializable` annotation only — JSON
  encoding lives in `:real`)
