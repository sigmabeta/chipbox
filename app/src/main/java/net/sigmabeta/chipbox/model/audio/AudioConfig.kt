package net.sigmabeta.chipbox.model.audio

data class AudioConfig(val sampleRate: Int,
                       val bufferSizeBytes: Int,
                       val minimumLatency: Int,
                       val bufferSizeShorts: Int = bufferSizeBytes / 2,
                       val bufferSizeSamples: Int = bufferSizeBytes / 4)