package net.sigmabeta.chipbox.readers

/**
 * Codec allow-lists for the streamed-audio readers — the codecs the vendored `libvgmstream` build
 * actually compiles in. One map, keyed by [Format], is the single source of truth so a reader
 * cannot drift from the build or from a sibling reader's predicate.
 *
 * **These lists must mirror the build, not upstream vgmstream's documentation.** The vendored core
 * (`cbox/native/vgmstream/Core/vgmstream/src/`) is compiled by
 * `cbox/native/vgmstream/CMakeLists.txt` with exactly one definition, `VGM_LOG_OUTPUT`, and every
 * external-codec switch in `src/vgmstream.h:6-18` (`VGM_USE_VORBIS`, `VGM_USE_MPEG`, `VGM_USE_FFMPEG`,
 * `VGM_USE_ATRAC9`, `VGM_USE_CELT`, `VGM_USE_G719`, `VGM_USE_G7221`, `VGM_USE_SPEEX`, …) is
 * commented out. A meta file with a case behind one of those `#ifdef`s compiles to a stub, so a bank
 * that needs that codec cannot be played and must yield no tracks.
 *
 * To re-check after a vendored-source update:
 * 1. `grep -n "VGM_USE" cbox/native/vgmstream/Core/vgmstream/src/vgmstream.h` — any uncommented
 *    define enables an external codec.
 * 2. `grep -n "compile_definitions" cbox/native/vgmstream/CMakeLists.txt` — only `VGM_LOG_OUTPUT`
 *    may appear.
 * 3. Re-read the `switch (fsb5.codec)` block in `src/meta/fsb5.c`, the codec2 table in
 *    `src/meta/ea_schl.c`, and the `switch (genh.codec)` block in `src/meta/genh.c`: ids inside an
 *    `#ifdef VGM_USE_*` are not decodable by this build.
 */
internal object StreamedCodecs {

    /** A streamed format whose header carries a codec id this build may or may not decode. */
    enum class Format { FSB5, EA_SCHL, GENH }

    // FMOD_SOUND_FORMAT_* ids used by FSB5 sample banks (`meta/fsb5.c:305-509`).
    private const val FMOD_PCM8 = 0x01
    private const val FMOD_PCM16 = 0x02
    private const val FMOD_PCM_FLOAT = 0x05
    private const val FMOD_GCADPCM = 0x06
    private const val FMOD_IMA_ADPCM = 0x07
    private const val FMOD_VAG = 0x08
    private const val FMOD_HEVAG = 0x09
    private const val FMOD_FADPCM = 0x10

    /**
     * FSB5 codec ids the vendored core actually decodes: PCM8/16/float, GCADPCM (GameCube DSP),
     * IMA ADPCM, VAG/HEVAG (PSX) and FADPCM. PCM24/PCM32 are excluded because `meta/fsb5.c` fails
     * them, and everything else in the FSB5 codec table needs FFmpeg/MPEG/Vorbis/CELT/ATRAC9.
     */
    private val FSB5_ALLOWED_CODECS: Set<Int> = setOf(
        FMOD_PCM8,
        FMOD_PCM16,
        FMOD_PCM_FLOAT,
        FMOD_GCADPCM,
        FMOD_IMA_ADPCM,
        FMOD_VAG,
        FMOD_HEVAG,
        FMOD_FADPCM,
    )

    // EA SCHl codec2 ids (`meta/ea_schl.c:48-75`).
    private const val EA_S16LE_INT = 0x00
    private const val EA_S16BE_INT = 0x01
    private const val EA_S8_INT = 0x02
    private const val EA_EAXA_INT = 0x03
    private const val EA_MT10 = 0x04
    private const val EA_S16BE = 0x07
    private const val EA_S16LE = 0x08
    private const val EA_S8 = 0x09
    private const val EA_EAXA = 0x0A
    private const val EA_MT5 = 0x16

    /**
     * EA SCHl codec2 ids this reader accepts: PCM16 LE/BE split and interleaved, PCM8 split and
     * interleaved, EA-XA (stereo/interleaved and split mono, which upstream decodes as `EA_XA_int`
     * or `EA_XA_V2` depending on the header version) and EA-ADPCM/MicroTalk (MT10/MT5).
     *
     * Everything else is rejected, so a scan never advertises audio this build cannot play:
     * - `0x0F`/`0x10` (MPEG Layer II/III) and `0x17` (EA Layer 3) are behind `VGM_USE_MPEG`.
     * - `0x1A`/`0x1B` (ATRAC3/ATRAC3plus) are behind `VGM_USE_FFMPEG`.
     * - `0x1C`+ are EAAC codecs; `meta/ea_schl.c` has no case for them either.
     * - VAG (`0x05`), N64 VADPCM (`0x06`), DVI IMA (`0x0D`), GCADPCM (`0x12`) and Xbox IMA
     *   (`0x14`) *are* built in, but are outside this ticket's EA-XA/EA-ADPCM/PCM scope.
     */
    private val EA_SCHL_ALLOWED_CODECS: Set<Int> = setOf(
        EA_S16LE_INT,
        EA_S16BE_INT,
        EA_S8_INT,
        EA_EAXA_INT,
        EA_MT10,
        EA_S16BE,
        EA_S16LE,
        EA_S8,
        EA_EAXA,
        EA_MT5,
    )

    // GENH codec ids (`meta/genh.c:8-40`, the `genh_type` enum).
    private const val GENH_PSX = 0
    private const val GENH_XBOX = 1
    private const val GENH_NGC_DTK = 2
    private const val GENH_PCM16BE = 3
    private const val GENH_PCM16LE = 4
    private const val GENH_PCM8 = 5
    private const val GENH_SDX2 = 6
    private const val GENH_DVI_IMA = 7
    private const val GENH_IMA = 9
    private const val GENH_AICA = 10
    private const val GENH_MSADPCM = 11
    private const val GENH_NGC_DSP = 12
    private const val GENH_PCM8_U_INT = 13
    private const val GENH_PSX_BF = 14
    private const val GENH_MS_IMA = 15
    private const val GENH_PCM8_U = 16
    private const val GENH_APPLE_IMA4 = 17
    private const val GENH_PCFX = 24
    private const val GENH_PCM4 = 25
    private const val GENH_PCM4_U = 26
    private const val GENH_OKI16 = 27

    /**
     * GENH codec ids whose `switch (genh.codec)` case is not behind a `VGM_USE_*` guard: every
     * built-in coder this build carries. `8` (MPEG), `18..23` (ATRAC3/ATRAC3plus, XMA1/2, FFmpeg,
     * AC3) and `28` (AAC) are compiled out, so a GENH wrapper around one of those yields no track
     * instead of a length the player cannot decode.
     */
    private val GENH_ALLOWED_CODECS: Set<Int> = setOf(
        GENH_PSX,
        GENH_XBOX,
        GENH_NGC_DTK,
        GENH_PCM16BE,
        GENH_PCM16LE,
        GENH_PCM8,
        GENH_SDX2,
        GENH_DVI_IMA,
        GENH_IMA,
        GENH_AICA,
        GENH_MSADPCM,
        GENH_NGC_DSP,
        GENH_PCM8_U_INT,
        GENH_PSX_BF,
        GENH_MS_IMA,
        GENH_PCM8_U,
        GENH_APPLE_IMA4,
        GENH_PCFX,
        GENH_PCM4,
        GENH_PCM4_U,
        GENH_OKI16,
    )

    /**
     * The codecs each [Format]'s vendored meta maps to a coder that this build compiles in.
     * Unknown ids are never allowed: absence from the set is the rejection, so nothing is
     * implicitly forward-compatible.
     */
    val ALLOWED_CODECS: Map<Format, Set<Int>> = mapOf(
        Format.FSB5 to FSB5_ALLOWED_CODECS,
        Format.EA_SCHL to EA_SCHL_ALLOWED_CODECS,
        Format.GENH to GENH_ALLOWED_CODECS,
    )

    /** True when this build can decode [format] audio with codec id [codec]. */
    fun isCodecAllowed(format: Format, codec: Int): Boolean = codec in ALLOWED_CODECS.getValue(format)
}
