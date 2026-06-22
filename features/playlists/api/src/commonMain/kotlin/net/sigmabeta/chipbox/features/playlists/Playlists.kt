package net.sigmabeta.chipbox.features.playlists

import kotlinx.serialization.Serializable

/**
 * The playlists list screen. With no [pendingTrackIds] it's the normal browse list (reached from the
 * Library screen). When launched with a non-empty [pendingTrackIds] it enters "picker" mode: an
 * "Add to Playlist" target chooser — tapping an existing playlist appends those tracks and pops back,
 * tapping New Playlist creates one with them and opens its detail.
 *
 * [suggestedName], when set, seeds the name of a playlist created from the picker (e.g. "From game
 * Street Fighter II"). The bulk-add entry points fill it from their source; the Library "New
 * Playlist" path leaves it null and falls back to the generic default name.
 */
@Serializable
data class Playlists(
    val pendingTrackIds: List<Long> = emptyList(),
    val suggestedName: String? = null,
)
