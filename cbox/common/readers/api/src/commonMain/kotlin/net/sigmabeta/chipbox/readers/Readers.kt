package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

sealed class Reader {
    /**
     * Version of this reader's parsing/length logic. Bump it whenever a change alters the
     * [RawTrack]s a given file yields (e.g. a corrected track length), so a later scan re-reads
     * files that an older version parsed. The value is stored on each persisted track and compared
     * against the current code in the scanner's folder-skip check — see
     * `Reader.version` / `Readers.readerVersionFor`.
     */
    open val version: Int = INITIAL_VERSION

    abstract fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>?

    companion object {
        const val INITIAL_VERSION = 1
    }
}

fun isPsfFamily(extension: String): Boolean = extension in PSF_FAMILY_EXTENSIONS

class Readers(hatchet: Hatchet) {
    val psf = PsfReader(hatchet)
    val nsf = NsfReader(hatchet)
    val nsfe = NsfeReader(hatchet)
    val gbs = GbsReader(hatchet)
    val spc = SpcReader(hatchet)
    val rsn = RsnReader(hatchet, spc)
    val vgm = VgmReader(hatchet)
    val m3u = M3uReader(hatchet)

    fun forExtension(extension: String): Reader? = when (extension) {
        EXT_PSF, EXT_MINIPSF,
        EXT_GSF, EXT_MINIGSF,
        EXT_PSF2, EXT_MINIPSF2,
        EXT_2SF, EXT_MINI2SF,
        EXT_SSF, EXT_MINISSF,
        EXT_DSF, EXT_MINIDSF,
        EXT_USF, EXT_MINIUSF,
        EXT_NCSF, EXT_MININCSF -> psf

        EXT_NSF -> nsf

        EXT_NSFE -> nsfe

        EXT_GBS -> gbs

        EXT_SPC -> spc

        EXT_RSN -> rsn

        EXT_VGM, EXT_VGZ -> vgm

        else -> null
    }

    /**
     * Version of the reader that handles [extension], for the scanner's folder-skip check.
     * Formats with no dedicated chiptune reader ([forExtension] returns null, e.g. vgmstream
     * streamed audio) report [NO_READER_VERSION] — their output is parser-agnostic and covered by
     * the scanner version instead.
     */
    fun readerVersionFor(extension: String): Int =
        forExtension(extension)?.version ?: NO_READER_VERSION

    companion object {
        const val NO_READER_VERSION = 0
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
private const val EXT_USF = "usf"
private const val EXT_MINIUSF = "miniusf"
private const val EXT_NCSF = "ncsf"
private const val EXT_MININCSF = "minincsf"
private const val EXT_NSF = "nsf"
private const val EXT_NSFE = "nsfe"
private const val EXT_GBS = "gbs"
private const val EXT_SPC = "spc"
private const val EXT_RSN = "rsn"
private const val EXT_VGM = "vgm"
private const val EXT_VGZ = "vgz"

private val PSF_FAMILY_EXTENSIONS = setOf(
    EXT_PSF, EXT_MINIPSF, EXT_GSF, EXT_MINIGSF,
    EXT_PSF2, EXT_MINIPSF2, EXT_2SF, EXT_MINI2SF,
    EXT_SSF, EXT_MINISSF, EXT_DSF, EXT_MINIDSF,
    EXT_USF, EXT_MINIUSF, EXT_NCSF, EXT_MININCSF,
)
