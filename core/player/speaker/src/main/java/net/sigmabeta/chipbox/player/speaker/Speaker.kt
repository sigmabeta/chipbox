package net.sigmabeta.chipbox.player.speaker

interface Speaker {
    suspend fun play(trackId: Long)
}
