package net.sigmabeta.chipbox.backend.vgm

import net.sigmabeta.chipbox.backend.Backend

class BackendImpl : Backend {
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
}