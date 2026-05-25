package net.sigmabeta.chipbox.common.ui.components.api.previews

/**
 * Cover art comes from IGDB, which serves covers at 528x704 — a 3:4 portrait ratio
 * (528 / 704 = 0.75). The surfaces that display cover art match this ratio so the whole
 * cover shows instead of being cropped to a square or stretched into a wide banner.
 *
 * Artists have no photo source yet (their `photoUrl` is always null), so their surfaces
 * just render the placeholder icon at this same ratio.
 */
object CoverArtConstants {
    const val ASPECT_RATIO = 0.75f
}
