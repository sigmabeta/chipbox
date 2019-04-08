package net.sigmabeta.chipbox.backend.usf

import net.sigmabeta.chipbox.backend.Backend

class BackendImpl : Backend {
    init {
        if (!INITIALIZED) {
            System.loadLibrary(NAME)
            INITIALIZED = true
        }
    }
    external override fun loadFile(filename: String, track: Int, sampleRate: Int, bufferSize: Long, fadeTimeMs: Long)

    external override fun readNextSamples(targetBuffer: ShortArray)

    external override fun getMillisPlayed(): Long

    external override fun seek(timeInMsec: Long): String

    external override fun setTempo(tempo: Double)

    external override fun getVoiceCount(): Int

    external override fun getVoiceName(position: Int): String

    external override fun muteVoice(voiceNumber: Int, enabled: Int)

    external override fun isTrackOver(): Boolean

    external override fun teardown()

    external override fun getLastError(): String?

    companion object {
        const val NAME = "usf"
        const val ID = 3

        var INITIALIZED = false
    }
}
