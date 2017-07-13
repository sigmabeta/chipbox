package net.sigmabeta.chipbox.model.audio

 class AudioConfig(val sampleRate: Int,
                       val minBufferSize: Int,
                       var bufferSizeMultiplier: Int,
                       var bufferCount: Int) {

    val bufferSizeBytes
        get() = minBufferSize * bufferCount

    val bufferSizeShorts
        get() = bufferSizeBytes / 2

    val bufferSizeSamples
        get() = bufferSizeBytes / 4

    val minimumLatency
        get() = 1000 * bufferSizeSamples / sampleRate

    val actualLatency
        get() =  minimumLatency * bufferSizeMultiplier

    val totalBufferSizeMs
        get() = bufferCount * actualLatency
}