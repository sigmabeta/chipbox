package net.sigmabeta.chipbox.browserstate

/** Stable `localStorage` keys. */
object BrowserStateKeys {
    const val FAVORITES = "chipbox.favorites"
    const val PLAYLISTS = "chipbox.playlists"

    /**
     * Play history. Kept at the key the pre-versioning web app used, so its bare JSON array is
     * migrated in place (read as schema version 0) rather than orphaned.
     */
    const val PLAYBACK_HISTORY = "chipbox.playback_history"

    /**
     * The one saved playback session. Matches the shared `RealPlaybackSessionStore` key so a raw
     * (unversioned) snapshot written by another build is read as schema version 0.
     */
    const val PLAYBACK_SESSION = "playback.session.snapshot"

    /** Recent searches, kept entirely in the browser (the server stores no user state). */
    const val SEARCH_HISTORY = "chipbox.search_history"
}
