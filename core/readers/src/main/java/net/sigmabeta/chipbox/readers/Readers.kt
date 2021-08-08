package net.sigmabeta.chipbox.readers

sealed class Reader {
    abstract fun readTracksFromFile(path: String): List<RawTrack>?
}

