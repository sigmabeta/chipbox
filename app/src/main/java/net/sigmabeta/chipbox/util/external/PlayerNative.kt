package net.sigmabeta.chipbox.util.external

import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.util.*
import java.io.File
import java.util.*

external fun loadFileGme(filename: String, track: Int, sampleRate: Int, bufferSize: Long, fadeTimeMs: Long)

external fun readNextSamplesGme(targetBuffer: ShortArray)

external fun getMillisPlayedGme(): Long

external fun seekNativeGme(timeInSec: Int): String

external fun setTempoNativeGme(tempo: Double)

external fun getVoiceCountNativeGme(): Int

external fun getVoiceNameNativeGme(position: Int): String

external fun muteVoiceNativeGme(voiceNumber: Int, enabled: Int)

external fun isTrackOverGme(): Boolean

external fun teardownGme()

external fun getLastErrorGme(): String?

fun getVoicesWrapper(): MutableList<Voice> {
    val voiceCount = getVoiceCountNativeGme()
    val voices = ArrayList<Voice>(voiceCount)

    for (index in 0 until voiceCount) {
        val voiceName = getVoiceNameNativeGme(index)
        val voice = Voice(index, voiceName)
        voices.add(voice)
    }

    return voices
}

fun loadTrackNative(track: Track, sampleRate: Int, bufferSizeShorts: Long) {
    val path = track.path.orEmpty()

    logDebug("[PlayerNative] Loading file: ${path}")

    val extension = File(path).extension
    val trackNumber = if (EXTENSIONS_MULTI_TRACK.contains(extension)) {
        (track.trackNumber ?: 1) - 1
    } else {
        0
    }

//    loadFileGme(path, trackNumber, sampleRate, bufferSizeShorts, track.trackLength ?: 60000)
    loadFileVgm(path)

    val titleByteArray = getFileTitleVgm()
    val title = titleByteArray?.convert()

    logInfo("File title: $title")

    val loadError = getLastErrorVgm()

    if (loadError != null) {
        logError("[PlayerNative] Unable to load file: $loadError")
    }
}