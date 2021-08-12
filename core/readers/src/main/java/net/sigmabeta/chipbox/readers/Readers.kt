package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.RawTrack

sealed class Reader {
    abstract fun readTracksFromFile(path: String): List<RawTrack>?
}

