package net.sigmabeta.chipbox.player.speaker.real

import net.sigmabeta.chipbox.player.speaker.Speaker
import timber.log.Timber

class RealSpeaker() : Speaker {
    override fun play(audio: ShortArray) {
        Timber.i(audio.copyOf().toReadableString())
    }

    private fun ShortArray.toReadableString(): String {
        val toString = StringBuilder().also {
            it.append("Bytes in buffer: \n")
            for (index in indices) {
                if (index % 2 == 0) {
                    it.append("${get(index)}, ")
                } else {
                    it.append("${get(index)}\n")
                }
            }
            it.append("End buffer.")
        }.toString()
        return toString
    }
}


