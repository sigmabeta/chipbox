package net.sigmabeta.chipbox.player.status.real

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.player.status.PlayerStatus
import net.sigmabeta.chipbox.player.status.StatusProvider


class RealStatusProvider() : StatusProvider {
    override fun updates(): Flow<PlayerStatus> {
        TODO("Not yet implemented")
    }
}