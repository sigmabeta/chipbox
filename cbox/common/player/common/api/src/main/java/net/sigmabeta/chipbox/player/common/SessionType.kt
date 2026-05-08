package net.sigmabeta.chipbox.player.common

/**
 * What a [Session]'s `contentId` refers to, and therefore how the director resolves the
 * setlist. `GAME` pulls every track for the given game; `ARTIST` is not yet implemented.
 */
enum class SessionType {
    GAME,
    ARTIST
}
