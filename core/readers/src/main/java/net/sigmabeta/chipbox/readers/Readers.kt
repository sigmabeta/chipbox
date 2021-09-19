package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack

sealed class Reader {
    abstract fun readTracksFromFile(path: String): List<RawTrack>?
}

fun getReaderForExtension(extension: String): Reader? {
    return when (extension) {
//        "psf" -> PsfReader
//        "minipsf" -> PsfReader
//        "gsf" -> PsfReader
//        "minigsf" -> PsfReader
        "usf" -> PsfReader
        "miniusf" -> PsfReader
//        "psf2" -> PsfReader
//        "minipsf2" -> PsfReader
//        "nsf" -> NsfReader
//        "nsfe" -> NsfeReader
//        "gbs" -> GbsReader
//        "spc" -> SpcReader
        else -> null
    }
}