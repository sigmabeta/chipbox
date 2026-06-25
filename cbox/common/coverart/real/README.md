# `:cbox:common:coverart:real`

> The production cover-art implementation over IGDB: cache, client, overrides, and the fetch loop.

The implementations behind the cover-art domain in
[`:cbox:common:coverart:api`](../api): the IGDB lookup client, the persistent
lookup cache, user overrides, the per-folder `igdb.txt` descriptor, and the
`CoverArtFetcher` that drives a "Get cover art" run. This is the `:real` module —
the concrete logic the app and CLI run. Almost everything is `commonMain` (files
via okio, time via `kotlin.time.Clock`, waits via coroutines), with the network
hidden behind the `CoverArtHttp` interface; the one platform-bound piece, the
OkHttp-backed `CoverArtHttp`, lives in `jvmSharedMain` (`src/main/java`) and is
shared by the Android + JVM targets. There is no `:coverart:di` module — the app
constructs and injects these types directly.

## Contents

**Fetch orchestration**
- `CoverArtFetcher.kt` — drives the run: per game, resolve a match (override →
  cache → folder `igdb.txt` → IGDB), download the cover into each track folder as
  `<Title>.jpg` (replacing existing images), and (re)write `igdb.txt`. Streams a
  `CoverArtResult` per game to a callback; one game's failure is recorded and the
  run continues. Also defines `CoverArtTally`, a thread-safe running count that
  snapshots to a `CoverArtSummary` (survives an early Ctrl-C).

**IGDB access** (the platform seam is an interface here, impl in `jvmSharedMain`)
- `IgdbClient.kt` — Twitch client-credentials auth, name search with simplified
  fallback candidates, platform-aware ranking, throttling/retries, and
  `coverUrl(imageId)`. Produces an `api` `CoverLookup`; talks to the net only via
  `CoverArtHttp`. Also `fetchGame(idOrSlug)` for manual overrides.
- `CoverArtHttp.kt` — the subsystem's only platform boundary: a small blocking
  HTTP interface (`post`/`postForm`/`getBytes`) throwing `okio.IOException`.
- `src/main/java/.../OkHttpCoverArtHttp.kt` — the JVM/Android `actual`-style impl
  over a caller-owned `OkHttpClient` (in `jvmSharedMain`).

**Persistence**
- `CoverArtCache.kt` — JSON cache of lookups keyed by `coverArtKey`; TTL-gated by
  `COVER_ART_TTL_DAYS`, thread-safe via an `AtomicReference` to an immutable map,
  tracks the last-downloaded URL to skip unchanged re-downloads.
- `CoverArtOverrides.kt` — separate JSON store of user-pinned `OverrideEntry`s;
  never expires, survives clearing the cache.
- `IgdbLinkFile.kt` — reads/writes the human-readable `igdb.txt` describing a
  folder's match; TTL-gated so a stale descriptor is re-queried rather than
  trusted forever.
- `CoverArtConfig.kt` — loads IGDB credentials from disk (parse is the `api`'s
  `parseIgdbCredentials`) and can stamp out a blank `[igdb]` template.
- `Atomics.kt` — internal `AtomicReference.update { }` CAS helper (no
  `synchronized` in commonMain), used by the cache and overrides.

Tests (`commonTest`, over okio's `FakeFileSystem`): `CoverArtCacheTest`,
`CoverArtOverridesTest`, `IgdbLinkFileTest` cover cache freshness/round-tripping,
override set/get/clear persistence, and `igdb.txt` read-back/staleness.

## Why depend on this module

Depend on `:cbox:common:coverart:real` when you actually run cover-art fetching —
the desktop JVM app and the CLI wire it. Depend on
[`:cbox:common:coverart:api`](../api) instead if you only need the lookup/result
types. `CoverArtHttp` is the platform seam: the commonMain logic takes the
interface, and a target supplies `OkHttpCoverArtHttp` (the same pattern the
scanner uses for its native prober). Note that `api`'s `CoverLookup` is an outcome
type, not a service interface — it's *produced* here by `IgdbClient.lookupCover`,
`CoverArtCache.get`, and `IgdbLinkFile.read`, not implemented by a single class.

## Using it

```kotlin
// Wire the pieces (the app owns the OkHttpClient and FileSystem), then run.
val http = OkHttpCoverArtHttp(okHttpClient)              // jvmSharedMain impl
val creds = CoverArtConfig.load(fileSystem, configPath) ?: return
val fetcher = CoverArtFetcher(
    igdb = IgdbClient(creds, http),
    http = http,
    cache = CoverArtCache(fileSystem, cachePath),
    overrides = CoverArtOverrides(fileSystem, overridesPath),
    fileSystem = fileSystem,
)

val tally = CoverArtTally()
fetcher.fetch(games) { result ->                          // streamed per game
    tally.record(result)
    println("${result.title}: ${result.outcome}")
}
println(tally.snapshot())                                 // CoverArtSummary
```

## Module facts

- **Plugin:** `sage.kmp` + `sage.kmp.js` + `kotlin.serialization`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js` (keeps the
  metadata compile honest about commonMain purity)
- **Source set:** `commonMain` for the logic; `src/main/java` (`jvmSharedMain`)
  for the OkHttp `CoverArtHttp` impl, shared by `androidMain` + `jvmMain`
- **SAGE/module dependencies:** `:cbox:common:coverart:api`,
  `:cbox:common:models:api`; `okio`, `kotlinx-serialization-json`,
  `kotlinx-coroutines-core` (commonMain); `okhttp` (`jvmSharedMain`)
