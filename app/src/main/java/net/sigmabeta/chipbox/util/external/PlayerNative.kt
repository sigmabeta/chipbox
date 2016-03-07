package net.sigmabeta.chipbox.util.external

import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.EXTENSIONS_MULTI_TRACK
import net.sigmabeta.chipbox.util.getFileExtension
import net.sigmabeta.chipbox.util.logDebug
import net.sigmabeta.chipbox.util.logError

external fun loadFile(filename: String, track: Int, sampleRate: Int, bufferSize: Long, fadeTimeMs: Long)

external fun readNextSamples(targetBuffer: ShortArray)

external fun getMillisPlayed(): Long

external fun seekNative(timeInSec: Int): String

external fun setTempoNative(tempo: Double)

external fun muteVoiceNative(voiceNumber: Int, enabled: Int)

external fun isTrackOver(): Boolean

external fun teardown()

external fun getLastError(): String?

fun loadTrackNative(track: Track, sampleRate: Int, bufferSizeShorts: Long) {
    val path = track.path

    logDebug("[PlayerNative] Loading file: ${path}")

    val extension = getFileExtension(path)
    val trackNumber = if (EXTENSIONS_MULTI_TRACK.contains(extension)) {
        track.trackNumber - 1
    } else {
        0
    }

    loadFile(path, trackNumber, sampleRate, bufferSizeShorts, track.trackLength)

    if (getLastError() != null) {
        logError("[PlayerNative] Unable to load file.")

        val loadError = getLastError()
        if (loadError != null) {
            logError("[PlayerNative] GME Error: ${loadError}")
        }
    }
}