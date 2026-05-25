package net.sigmabeta.chipbox.coverart

/** Outcome of an IGDB cover-art lookup for one game. */
sealed interface CoverLookup {
    /**
     * A game that matched on IGDB. [igdbId], [igdbName] and [igdbSlug] identify the matched game so
     * the link can be described in each game's folder (see `IgdbLinkFile`) and read back from there
     * to skip a future lookup. Any of them may be null for lookups cached or read back before that
     * data was recorded.
     */
    sealed interface Matched : CoverLookup {
        val igdbId: String?
        val igdbName: String?
        val igdbSlug: String?
    }

    /**
     * A matched game that has cover art with this IGDB [imageId]. The id (not a full URL) is the
     * stable, size-independent identity of the cover; the sized download URL is derived from it via
     * `IgdbClient.coverUrl`, so changing the image size doesn't invalidate cached lookups.
     */
    data class Found(
        val imageId: String,
        override val igdbId: String? = null,
        override val igdbName: String? = null,
        override val igdbSlug: String? = null,
    ) : Matched

    /** A matched game that has no cover art on IGDB. */
    data class NoCover(
        override val igdbId: String? = null,
        override val igdbName: String? = null,
        override val igdbSlug: String? = null,
    ) : Matched

    /** No game on IGDB matched the searched name. */
    data object NoMatch : CoverLookup
}

/**
 * A single IGDB game resolved by id/slug: its numeric [id], [name], URL [slug], and cover image id
 * ([imageId], null if it has no cover).
 */
data class IgdbGameInfo(val id: String, val name: String, val slug: String, val imageId: String?)
