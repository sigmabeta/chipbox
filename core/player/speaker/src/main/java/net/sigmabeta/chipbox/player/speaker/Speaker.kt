package net.sigmabeta.chipbox.player.speaker

import net.sigmabeta.chipbox.player.common.AudioBuffer

interface Speaker {
    fun play(audio: AudioBuffer)
}
