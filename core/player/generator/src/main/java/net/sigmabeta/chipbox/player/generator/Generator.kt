package net.sigmabeta.chipbox.player.generator

interface Generator {
    suspend fun play(trackId: Long)
}