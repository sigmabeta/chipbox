package net.sigmabeta.chipbox.player.director

/**
 * One slot in the live play queue. [slotId] is a stable identity minted by the director when the
 * slot enters the queue and preserved across reorders and removals — it is deliberately independent
 * of the slot's position so the same [trackId] can appear more than once (e.g. a queued duplicate)
 * without two slots colliding. UI keys and per-slot actions key off [slotId], never [trackId] or
 * position.
 *
 * [active] marks the one slot the director is currently playing (its position is the session's
 * `currentPosition`); with duplicate track ids this is the only reliable way to know *which* copy
 * is playing.
 */
data class SetlistEntry(
    val slotId: Long,
    val trackId: Long,
    val active: Boolean,
)
