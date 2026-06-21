package net.sigmabeta.chipbox.playlists.dao

import androidx.room.Embedded
import net.sigmabeta.chipbox.entities.PlaylistEntity

/**
 * Projection of a [PlaylistEntity] plus its member count, computed by a `LEFT JOIN … GROUP BY` so the
 * playlists list (and the detail header) can show each playlist's size without hydrating its tracks.
 */
data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val trackCount: Int,
)
