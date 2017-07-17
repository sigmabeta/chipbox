package net.sigmabeta.chipbox.model.audio

class AudioConfig(val sampleRate: Int,
                  val minBufferSizeBytes: Int,
                  var bufferSizeMultiplier: Int,
                  var bufferCount: Int) {
    val NUMBER_OF_CHANNELS = 2

    val BYTES_PER_SHORT = 2

    val minBufferSizeShorts
        get() = minBufferSizeBytes / BYTES_PER_SHORT

    val minBufferSizeSamples
        get() = minBufferSizeShorts / (NUMBER_OF_CHANNELS)

    val singleBufferSizeBytes
        get() = minBufferSizeBytes * bufferSizeMultiplier

    val singleBufferSizeShorts
        get() = singleBufferSizeBytes / BYTES_PER_SHORT

    val singleBufferSizeSamples
        get() = singleBufferSizeShorts / NUMBER_OF_CHANNELS

    val totalBufferSizeBytes
        get() = singleBufferSizeBytes * bufferCount

    val totalBufferSizeShorts
        get() = totalBufferSizeBytes / BYTES_PER_SHORT

    val totalBufferSizeSamples
        get() = totalBufferSizeShorts / NUMBER_OF_CHANNELS

    val minimumLatency
        get() = 1000 * minBufferSizeSamples / sampleRate

    val singleBufferLatency
        get() = minimumLatency * bufferSizeMultiplier

    val totalBufferSizeMs
        get() = singleBufferLatency * bufferCount
}