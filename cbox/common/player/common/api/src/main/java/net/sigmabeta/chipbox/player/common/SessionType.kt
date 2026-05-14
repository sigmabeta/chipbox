package net.sigmabeta.chipbox.player.common

/**
 * What a [Session]'s `contentId` refers to, and therefore how the director resolves the
 * setlist. Only `GAME` is implemented in [net.sigmabeta.chipbox.player.director.Director]
 * today; the other variants exist so the UI (e.g. the now-playing screen's session summary)
 * can describe sessions whose director plumbing is still being wired up.
 *
 * - `GAME` / `ARTIST` / `PLAYLIST` — `contentId` is the foreign-key id of the source
 *   collection, used both to resolve the setlist and to look up its display name.
 * - `ALL_TRACKS` — the entire library; `contentId` is unused (callers may pass `0`).
 */
enum class SessionType {
    GAME,
    ARTIST,
    PLAYLIST,
    ALL_TRACKS,
}
