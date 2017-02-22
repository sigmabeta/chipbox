package net.sigmabeta.chipbox.backend


interface Backend {
    fun loadFile(filename: String, track: Int, sampleRate: Int, bufferSize: Long, fadeTimeMs: Long)

    fun readNextSamples(targetBuffer: ShortArray)

    fun getMillisPlayed(): Long

    fun seek(timeInSec: Int): String

    fun setTempo(tempo: Double)

    fun getVoiceCount(): Int

    fun getVoiceName(position: Int): String

    fun muteVoice(voiceNumber: Int, enabled: Int)

    fun isTrackOver(): Boolean

    fun teardown()

    fun getLastError(): String?

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

