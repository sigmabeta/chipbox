package net.sigmabeta.chipbox.artworkprovider.api

import android.net.Uri

object ArtworkUris {
    /** Suffix appended to the applicationId to form the provider authority. Must match the
     *  `${applicationId}` authority declared in the artwork-provider manifest. */
    const val AUTHORITY_SUFFIX = "artworkprovider.api"

    /**
     * The artwork ContentProvider's authority. Derived from the applicationId so a `.debug` build
     * (authority `net.sigmabeta.chipbox.debug.artworkprovider.api`) doesn't collide with a release
     * install (`net.sigmabeta.chipbox.artworkprovider.api`) — two apps may not declare the same
     * provider authority. Set once from `Context.packageName` in `ChipboxApplication.attachBaseContext`
     * (which runs before the provider's `onCreate` and before any URI is built); the default is the
     * release value so anything reading it before that — or in a host that doesn't set it — still
     * works. `@Volatile` for visibility across the threads that build artwork URIs.
     */
    @Volatile
    var authority: String = "net.sigmabeta.chipbox.$AUTHORITY_SUFFIX"

    const val SEGMENT_GAME = "game"
    const val SEGMENT_ARTIST = "artist"

    fun forGame(id: Long): Uri = Uri.parse("content://$authority/$SEGMENT_GAME/$id")

    fun forArtist(id: Long): Uri = Uri.parse("content://$authority/$SEGMENT_ARTIST/$id")
}
