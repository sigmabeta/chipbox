package net.sigmabeta.chipbox.player.status

import kotlinx.coroutines.flow.Flow

/**
 * Read-only window onto the player's current status, intended for callers that don't need the
 * full reducer-driven [net.sigmabeta.chipbox.player.director.ChipboxPlaybackState] surfaced by
 * the director (e.g. system widgets, telemetry). Currently unimplemented; see
 * [net.sigmabeta.chipbox.player.status.real.RealStatusProvider].
 */
interface StatusProvider {
    fun updates(): Flow<PlayerStatus>
}
