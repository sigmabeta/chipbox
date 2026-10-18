package net.sigmabeta.chipbox.browserstate

/** The codecs for the two browser stores. Add history/session codecs here in ticket 07. */
object BrowserStateCodecs {
    const val CURRENT_VERSION = 1

    val favorites: VersionedJsonCodec<FavoritesState> =
        VersionedJsonCodec(FavoritesState.serializer(), CURRENT_VERSION)

    val playlists: VersionedJsonCodec<PlaylistsState> =
        VersionedJsonCodec(PlaylistsState.serializer(), CURRENT_VERSION)
}
