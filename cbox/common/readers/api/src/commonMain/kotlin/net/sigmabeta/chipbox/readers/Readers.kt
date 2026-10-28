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
    val strm = StrmReader(hatchet)
    val genh = GenhReader(hatchet)
    val seb = SebReader(hatchet)
    val sadl = SadlReader(hatchet)
    val xa = XaReader(hatchet)
    val brstm = BrstmReader(hatchet)
    val brstmSpm = BrstmReader(hatchet, superPaperMario = true)
    val bfstm = BfstmReader(hatchet)
    val bcstm = BcstmReader(hatchet)
    val dsp = DspReader(hatchet)
    val fsb5 = Fsb5Reader(hatchet)
    val adx = AdxReader(hatchet)
    val hca = HcaReader(hatchet)
    val awb = AwbReader(hatchet)
    val acb = AcbReader(hatchet)
    val aix = AixReader(hatchet)
    val eaSchl = EaSchlReader(hatchet)
    val vag = VagReader(hatchet)
    val vab = VabReader(hatchet)
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

        EXT_STRM -> strm

        EXT_GENH -> genh

        EXT_SEB, EXT_GMS -> seb

        EXT_SAD -> sadl

        EXT_XA -> xa

        EXT_BRSTM -> brstm

        EXT_BRSTMSPM -> brstmSpm

        EXT_BFSTM -> bfstm

        EXT_BCSTM -> bcstm

        EXT_DSP -> dsp

        EXT_FSB, EXT_FSB5 -> fsb5

        EXT_ADX -> adx

        EXT_HCA -> hca

        EXT_AWB, EXT_AFS2 -> awb

        EXT_ACB -> acb

        EXT_AIX -> aix

        in EA_SCHL_EXTENSIONS -> eaSchl

        EXT_VAG -> vag

        EXT_VAB -> vab

        else -> null
    }

    /**
     * Version of the reader that handles [extension], for the scanner's folder-skip check.
     * Formats with no dedicated reader ([forExtension] returns null) report [NO_READER_VERSION];
     * a newly registered streamed format automatically reports its reader's real version, which
     * makes folders that stored the old sentinel stale and re-reads them.
     */
    fun readerVersionFor(extension: String): Int = forExtension(extension)?.version ?: NO_READER_VERSION

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
private const val EXT_STRM = "strm"
private const val EXT_GENH = "genh"
private const val EXT_SEB = "seb"
private const val EXT_GMS = "gms"
private const val EXT_SAD = "sad"
private const val EXT_BRSTM = "brstm"
private const val EXT_BRSTMSPM = "brstmspm"
private const val EXT_BFSTM = "bfstm"
private const val EXT_BCSTM = "bcstm"
private const val EXT_DSP = "dsp"
private const val EXT_XA = "xa"
private const val EXT_FSB = "fsb"
private const val EXT_FSB5 = "fsb5"

private val PSF_FAMILY_EXTENSIONS = setOf(
    EXT_PSF, EXT_MINIPSF, EXT_GSF, EXT_MINIGSF,
    EXT_PSF2, EXT_MINIPSF2, EXT_2SF, EXT_MINI2SF,
    EXT_SSF, EXT_MINISSF, EXT_DSF, EXT_MINIDSF,
    EXT_USF, EXT_MINIUSF, EXT_NCSF, EXT_MININCSF,
)
