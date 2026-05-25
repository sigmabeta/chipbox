package net.sigmabeta.chipbox.coverart

import kotlinx.serialization.Serializable

/**
 * A manual link from a Chipbox game to a specific IGDB game's cover. [igdbSlug] is the game's URL
 * slug (used to describe the link in its folder) and is null for links pinned before it was recorded.
 */
@Serializable
data class OverrideEntry(
    val igdbId: String,
    val igdbName: String,
    val imageId: String,
    val igdbSlug: String? = null,
)
