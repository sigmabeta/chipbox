package net.sigmabeta.chipbox.model.objects

class AudioBuffer(val bufferSizeBytes: Int) {
    var bufferFull = false
    val buffer = ShortArray(bufferSizeBytes)
}