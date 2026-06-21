package net.sigmabeta.chipbox.features.playlists

import kotlinx.serialization.Serializable

/**
 * The playlists list screen. With no [pendingTrackIds] it's the normal browse list (reached from the
 * Library screen). When launched with a non-empty [pendingTrackIds] it enters "picker" mode: an
 * "Add to Playlist" target chooser — tapping an existing playlist appends those tracks and pops back,
 * tapping New Playlist creates one with them and opens its detail.
 */
@Serializable
data class Playlists(val pendingTrackIds: List<Long> = emptyList())
