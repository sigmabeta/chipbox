package net.sigmabeta.chipbox.util.external

import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.logDebug
import net.sigmabeta.chipbox.util.logError

external fun loadFile(filename: String, track: Int, sampleRate: Int, bufferSize: Long)

external fun readNextSamples(targetBuffer: ShortArray)

external fun getMillisPlayed(): Int

external fun seekNative(timeInSec: Int): String

external fun setTempoNative(tempo: Double)

external fun muteVoiceNative(voiceNumber: Int, enabled: Int)

external fun isTrackOver(): Boolean

external fun teardown()

external fun getLastError(): String?

fun loadTrackNative(track: Track, sampleRate: Int, bufferSize: Long) {
    val path = track.path

    logDebug("[PlayerNative] Loading file: ${path}")
    loadFile(path, 0, sampleRate, bufferSize)

    if (getLastError() != null) {
        logError("[PlayerNative] Unable to load file.")

        val loadError = getLastError()
        if (loadError != null) {
            logError("[Player] GME Error: ${loadError}")
        }
    }
}