package net.sigmabeta.chipbox.model.audio

class AudioBuffer(val bufferSizeBytes: Int) {
    val buffer = ShortArray(bufferSizeBytes)
}