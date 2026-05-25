package net.sigmabeta.chipbox.player.common

/**
 * What a [Session]'s `contentId` refers to, and therefore how the director resolves the
 * setlist. `GAME` and `ARTIST` are wired through to the director; `PLAYLIST` and `ALL_TRACKS`
 * exist so the UI (e.g. the now-playing screen's session summary) can describe sessions whose
 * director plumbing is still being wired up.
 *
 * - `GAME` / `ARTIST` / `PLAYLIST` — `contentId` is the foreign-key id of the source
 *   collection, used both to resolve the setlist and to look up its display name.
 * - `ALL_TRACKS` — the entire library; `contentId` is unused (callers may pass `0`).
 * - `PLATFORM` — every track whose platform matches; `contentId` is not a foreign key
 *   but the `Platform.ordinal` (resolve via `Platform.entries[contentId.toInt()]`).
 * - `SETLIST` — an explicit, caller-supplied list of track ids carried in
 *   `Session.explicitSetlist`; `contentId` is unused (callers may pass `0`). Used for
 *   ad-hoc queues that aren't backed by a repository collection (e.g. search results).
 */
enum class SessionType {
    GAME,
    ARTIST,
    PLAYLIST,
    ALL_TRACKS,
    PLATFORM,
    SETLIST,
}
