package net.sigmabeta.chipbox.browserstate

import kotlinx.serialization.builtins.ListSerializer
import net.sigmabeta.chipbox.player.persistence.SessionSnapshot

/** The codecs for the browser-local stores. */
object BrowserStateCodecs {
    const val CURRENT_VERSION = 1

    val favorites: VersionedJsonCodec<FavoritesState> =
        VersionedJsonCodec(FavoritesState.serializer(), CURRENT_VERSION)

    val playlists: VersionedJsonCodec<PlaylistsState> =
        VersionedJsonCodec(PlaylistsState.serializer(), CURRENT_VERSION)

    /** Browser-local recent searches; the server never sees them. */
    val searchHistory: VersionedJsonCodec<SearchHistoryState> =
        VersionedJsonCodec(SearchHistoryState.serializer(), CURRENT_VERSION)

    /**
     * Play history. The pre-versioning web app stored a bare JSON array of plays at
     * `chipbox.playback_history`; that array is the schema-0 shape and is read through
     * [legacyDecoder]. The next recorded play rewrites it as the current envelope.
     */
    val playHistory: VersionedJsonCodec<PlayHistoryState> = VersionedJsonCodec(
        payloadSerializer = PlayHistoryState.serializer(),
        currentVersion = CURRENT_VERSION,
        legacyDecoder = { raw ->
            runCatching {
                VersionedJsonCodec.DEFAULT_JSON
                    .decodeFromString(ListSerializer(PlayHistoryEntry.serializer()), raw)
            }.getOrNull()?.let(::PlayHistoryState)
        },
    )

    /**
     * The saved playback session. A raw `SessionSnapshot` object (no `schemaVersion` header) was
     * written by `RealPlaybackSessionStore` before the web used the versioned store, so it is the
     * schema-0 shape; the next save rewrites it inside the envelope.
     */
    val playbackSession: VersionedJsonCodec<PlaybackSessionState> = VersionedJsonCodec(
        payloadSerializer = PlaybackSessionState.serializer(),
        currentVersion = CURRENT_VERSION,
        legacyDecoder = { raw ->
            runCatching {
                VersionedJsonCodec.DEFAULT_JSON.decodeFromString(SessionSnapshot.serializer(), raw)
            }.getOrNull()?.let(::PlaybackSessionState)
        },
    )
}
