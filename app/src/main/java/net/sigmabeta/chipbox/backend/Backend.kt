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
        val GME by lazy { net.sigmabeta.chipbox.backend.gme.BackendImpl() }
        val VGM by lazy { net.sigmabeta.chipbox.backend.vgm.BackendImpl() }
        val PSF by lazy { net.sigmabeta.chipbox.backend.psf.BackendImpl() }
        val USF by lazy { net.sigmabeta.chipbox.backend.usf.BackendImpl() }

        // TODO should this also be lazy?
        val IMPLEMENTATIONS = arrayOf(
                GME,
                VGM,
                PSF,
                USF
        )
    }
}

