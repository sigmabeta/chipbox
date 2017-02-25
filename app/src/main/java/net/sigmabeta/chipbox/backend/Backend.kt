package net.sigmabeta.chipbox.backend

import net.sigmabeta.chipbox.model.audio.Voice
import java.util.*


interface Backend {
    fun loadFile(filename: String, track: Int, sampleRate: Int, bufferSize: Long, fadeTimeMs: Long)

    fun readNextSamples(targetBuffer: ShortArray)

    fun getMillisPlayed(): Long

    fun seek(timeInMsec: Long): String

    fun setTempo(tempo: Double)

    fun getVoiceCount(): Int

    fun getVoiceName(position: Int): String

    fun muteVoice(voiceNumber: Int, enabled: Int)

    fun isTrackOver(): Boolean

    fun teardown()

    fun getLastError(): String?

    fun getVoices(): MutableList<Voice> {
        val voiceCount = getVoiceCount()
        val voices = ArrayList<Voice>(voiceCount)

        for (index in 0 until voiceCount) {
            val voiceName = getVoiceName(index)
            val voice = Voice(index, voiceName ?: "Voice $index")
            voices.add(voice)
        }

        return voices
    }

    companion object {
        val GME = net.sigmabeta.chipbox.backend.gme.BackendImpl()
        val VGM = net.sigmabeta.chipbox.backend.vgm.BackendImpl()

        val ID_GME = 0
        val ID_VGM = 1

        val IMPLEMENTATIONS = arrayOf(
                GME,
                VGM
        )
    }
}

