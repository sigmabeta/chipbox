package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack

sealed class Reader {
    abstract fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>?
}

fun isPsfFamily(extension: String): Boolean = extension in PSF_FAMILY_EXTENSIONS

fun getReaderForExtension(extension: String): Reader? {
    return when (extension) {
        EXT_PSF -> PsfReader
        EXT_MINIPSF -> PsfReader
        EXT_GSF -> PsfReader
        EXT_MINIGSF -> PsfReader
        EXT_PSF2 -> PsfReader
        EXT_MINIPSF2 -> PsfReader
        EXT_2SF -> PsfReader
        EXT_MINI2SF -> PsfReader
        EXT_SSF -> PsfReader
        EXT_MINISSF -> PsfReader
        EXT_DSF -> PsfReader
        EXT_MINIDSF -> PsfReader
        EXT_NSF -> NsfReader
        EXT_NSFE -> NsfeReader
        EXT_GBS -> GbsReader
        EXT_SPC -> SpcReader
        else -> null
    }
}

private const val EXT_PSF = "psf"
private const val EXT_MINIPSF = "minipsf"
private const val EXT_GSF = "gsf"
private const val EXT_MINIGSF = "minigsf"
private const val EXT_PSF2 = "psf2"
private const val EXT_MINIPSF2 = "minipsf2"
private const val EXT_2SF = "2sf"
private const val EXT_MINI2SF = "mini2sf"
private const val EXT_SSF = "ssf"
private const val EXT_MINISSF = "minissf"
private const val EXT_DSF = "dsf"
private const val EXT_MINIDSF = "minidsf"
private const val EXT_NSF = "nsf"
private const val EXT_NSFE = "nsfe"
private const val EXT_GBS = "gbs"
private const val EXT_SPC = "spc"

private val PSF_FAMILY_EXTENSIONS = setOf(
    EXT_PSF, EXT_MINIPSF, EXT_GSF, EXT_MINIGSF,
    EXT_PSF2, EXT_MINIPSF2, EXT_2SF, EXT_MINI2SF,
    EXT_SSF, EXT_MINISSF, EXT_DSF, EXT_MINIDSF,
)