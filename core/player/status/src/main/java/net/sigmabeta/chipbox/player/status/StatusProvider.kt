package net.sigmabeta.chipbox.player.status

import kotlinx.coroutines.flow.Flow

interface StatusProvider {
    fun updates(): Flow<PlayerStatus>
}