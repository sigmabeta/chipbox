package net.sigmabeta.chipbox.model.objects

class AudioBuffer(val bufferSizeBytes: Int) {
    val buffer = ShortArray(bufferSizeBytes)
}