package net.sigmabeta.chipbox.util.external

external fun loadFileVgm(filename: String)

external fun readNextSamplesVgm(targetBuffer: ShortArray, sampleCount: Int)

external fun teardownVgm()

external fun getLastErrorVgm(): String?
