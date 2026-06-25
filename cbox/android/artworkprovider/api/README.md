# `:cbox:android:artworkprovider:api`

> Read-only Android `ContentProvider` that streams game/artist artwork to system clients.

Android system glue (`:api`): a `ContentProvider` that serves cover art for `content://`
URIs so that the system media UI (e.g. Android Auto / the media notification) can load
artwork. It is Android-only — there is no cross-platform `:api` counterpart; the role of
`:api` here is "platform system component + its public URI contract", not a KMP interface.

## Contents

| File | What it is |
| --- | --- |
| `ArtworkProvider.kt` | The `ContentProvider`. Matches `game/#` and `artist/#` URIs, resolves the photo URL from `Repository`, downloads it via `LocalFileContentSource`, caches under `cacheDir/artwork`, and returns a read-only `ParcelFileDescriptor`. Read-only — `insert`/`update`/`delete`/`query` throw or return null. |
| `ArtworkUris.kt` | The URI contract: `authority` (applicationId-derived, settable so `.debug` and release don't collide), `SEGMENT_GAME`/`SEGMENT_ARTIST`, and `forGame(id)`/`forArtist(id)` URI builders. |
| `ArtworkProviderGraph.kt` | Narrow interface the app's `Application` implements so the provider can pull `repository()` + `fileContentSource()` off the Metro graph without depending on `apps/android` (avoids a dependency cycle). |
| `AndroidManifest.xml` | Registers the provider under the `${applicationId}.artworkprovider.api` authority. |

## Why depend on this module

Depend on it to build artwork `Uri`s (`ArtworkUris.forGame`/`forArtist`) for Media3
`MediaMetadata`, or to host the provider in the app. The app implements
`ArtworkProviderGraph` on its `Application` so the provider can reach the Metro graph.
The `:cbox:android:services:api` transformers use `ArtworkUris` to attach artwork to
browsable media items.

## Using it

```kotlin
// Building an artwork URI for a Media3 MediaMetadata:
metadata.setArtworkUri(ArtworkUris.forGame(game.id))

// The app's Application satisfies the provider's dependencies:
class ChipboxApplication : Application(), ArtworkProviderGraph {
    override fun repository(): Repository = appGraph.repository
    override fun fileContentSource(): LocalFileContentSource = appGraph.fileContentSource
}
```

## Module facts

- **Plugin:** `sage.android` + `sage.di`
- **Targets:** Android only
- **Source set:** `src/main/java`
- **SAGE/module dependencies:** `:cbox:common:repository:api`, `:cbox:common:contentsource:file:real`, `:cbox:common:models:api`
