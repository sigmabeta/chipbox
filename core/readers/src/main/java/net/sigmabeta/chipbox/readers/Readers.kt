package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack

sealed class Reader {
    abstract fun readTracksFromFile(path: String): List<RawTrack>?
}

fun getReaderForExtension(extension: String): Reader? {
    return when (extension) {
        "psf" -> PsfReader
        "nsfe" -> NsfeReader
        else -> null
    }
}