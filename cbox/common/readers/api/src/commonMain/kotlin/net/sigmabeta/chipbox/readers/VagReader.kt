package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * Sony VAG (`.vag`) — PS-ADPCM streams written by the PlayStation SDK and its many PS2-era custom
 * descendants. Ports the metadata layer of vgmstream's `meta/vag.c`: the header id selects a
 * layout, which fixes the data start, channel count/interleave and where the sample count and rate
 * live; loop points come either from PS-ADPCM frame flags (`ps_find_loop_offsets`) or from a
 * variant-specific header field.
 *
 * Only the metadata layer is ported — one [RawTrack] per file. Length follows the native probe's
 * play count (`base/play_state.c`): two loop iterations plus the configured ten-second fade when
 * looped, else the stream's sample count, with truncating integer division to milliseconds.
 */
class VagReader(private val hatchet: Hatchet) : Reader() {
    override val version: Int = VAG_VERSION

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        try {
            val stream = parseVag(bytes)
            if (stream == null) {
                hatchet.w("VAG parse failed: $identifier is not a supported VAG header.")
                null
            } else {
                listOf(stream.toTrack(identifier))
            }
        } catch (e: IndexOutOfBoundsException) {
            hatchet.w("VAG parse failed: $identifier has a truncated header (${e.message}).")
            null
        }

    companion object {
        const val VAG_VERSION = 1
    }
}

/** One parsed VAG stream, reduced to what a [RawTrack] needs. */
private data class VagStream(
    val sampleRate: Int,
    val numSamples: Long,
    val loop: VagLoop?,
    val title: String?,
) {
    /**
     * Play length in milliseconds, matching the native probe: a looped stream plays its intro plus
     * two full loop iterations plus the ten-second fade (`base/play_state.c`, `loop_count = 2`,
     * `fade_time = 10`); a non-looped stream plays its sample count. Integer division truncates.
     */
    private val lengthMs: Long
        get() = streamedLengthMs(
            numSamples = numSamples,
            loopFlag = loop != null,
            loopStartSample = loop?.start ?: 0L,
            loopEndSample = loop?.end ?: 0L,
            sampleRate = sampleRate,
        )

    fun toTrack(identifier: String) = streamedRawTrack(
        identifier = identifier,
        lengthMs = lengthMs,
        title = title.orUnknown(),
        platform = Platform.PSX,
    )
}

/** Loop points in samples: the first sample to repeat and the sample after the last one. */
internal data class VagLoop(val start: Long, val end: Long)

/**
 * Dispatches on the header id. vgmstream tries `init_vgmstream_vag` first (whose magic check only
 * accepts the `VAG?` ids and little-endian `pGAV`), then the `AAAp`, data-first "footer" and
 * Evolution Games variants, all for the `.vag` extension; this mirrors that order.
 */
private fun parseVag(bytes: ByteArray): VagStream? {
    val reader = bytesAsReader(bytes)
    return when {
        reader.size < MIN_VAG_SIZE -> null

        reader.hasAsciiPrefix(MAGIC_VAGP) -> parseVagp(reader)

        reader.hasAsciiPrefix(MAGIC_VAG1) -> parseVag1(reader)

        reader.hasAsciiPrefix(MAGIC_VAG2) -> parseVag2(reader)

        reader.hasAsciiPrefix(MAGIC_VAGI) -> parseVagi(reader)

        reader.hasAsciiPrefix(MAGIC_PGAV) -> parsePgav(reader)

        reader.hasAsciiPrefix(MAGIC_AAAP) -> parseAaap(reader)

        reader.hasAsciiPrefix(MAGIC_EVOLUTION) -> parseEvolution(reader)

        // No start magic: the stream comes first and a 0x40-byte VAGp "footer" closes the file.
        else -> parseFooter(reader)
    }
}

/**
 * `VAGp`, the standard PS1/PS2 header generation plus its many PS2 layout variations. Every branch
 * starts from the big-endian baseline at 0x0C/0x10 and overrides the fields it owns.
 */
private fun parseVagp(reader: ByteReader): VagStream? {
    val layout = readVagpLayout(reader) ?: return null
    return layout.toStream(reader)
}

private fun readVagpLayout(reader: ByteReader): VagLayout? {
    val base = baseVagLayout(reader) ?: return null
    val fileSize = reader.size.toLong()
    val version = reader.readIntBeAt(VERSION_OFFSET)
    val reserved = reader.readIntBeAt(RESERVED_OFFSET)

    return when {
        // The Simpsons Wrestling (PS1) / Shikigami no Shiro (PS2): a second header at an interleave
        // boundary marks a stereo stream whose first block includes the header.
        reader.hasAsciiAt(SECOND_HEADER_6000, MAGIC_VAGP) ->
            base.copy(channels = 2, interleave = INTERLEAVE_6000)

        reader.hasAsciiAt(SECOND_HEADER_1000, MAGIC_VAGP) ->
            base.copy(channels = 2, interleave = INTERLEAVE_1000).scanned(reader)

        version == VERSION_2_0 && reader.hasAsciiAt(SECOND_HEADER_800, MAGIC_VAGP) ->
            base.copy(channels = 2, interleave = INTERLEAVE_800)

        version == VERSION_EOR || version == VERSION_KILLZONE -> eorLayout(reader, version)

        version == VERSION_HEVAG_2_1 || version == VERSION_HEVAG_3_0 -> hevagLayout(reader, base)

        version == VERSION_KH2 && base.channelSize == fileSize - KH2_DATA_OFFSET &&
            reader.readIntBeAt(RESERVED_OFFSET) != 0 -> kh2Layout(reader, base)

        version == VERSION_2_0 && reader.hasAsciiAt(0x30, "STER") &&
            reader.hasAsciiAt(0x34, "EOVA") && reader.hasAsciiAt(0x38, "G2K\u0000") ->
            base.copy(startOffset = 0x800, channels = 2, interleave = INTERLEAVE_800)

        version == VERSION_NFS && reader.hasAsciiAt(0x24, "VAGx") -> vagxLayout(reader, base)

        version == VERSION_2_0 && base.channelSize == fileSize - GARFIELD_DATA_OFFSET &&
            reader.readIntBeAt(RESERVED_OFFSET) == 1 -> garfieldLayout(reader, base)

        version == VERSION_2_0 && reserved == RESERVED_EKO -> ekoLayout(reader, base)

        version == VERSION_2_0 && base.channelSize == fileSize + THQ_SIZE_BIAS ->
            thqLayout(reader, base)

        version == VERSION_2_0 && reader.hasAsciiAt(0x20, "KAudioDL") &&
            kaudioSizeMatches(reader, base) -> kaudioLayout(reader, base)

        else -> standardLayout(reader, base, version)
    }
}

/** Reads the baseline header fields shared by every `VAGp` branch (big-endian). */
private fun baseVagLayout(reader: ByteReader): VagLayout? {
    val sampleRate = reader.readIntBeAt(SAMPLE_RATE_OFFSET)
    val channelSize = reader.readIntBeAt(CHANNEL_SIZE_OFFSET).toLong() and UINT32_MASK
    return if (sampleRate > 0) VagLayout(channelSize = channelSize, sampleRate = sampleRate) else null
}

/** Edge of Reality (`0x02`) and Killzone (`0x40`): header is 0x40 bytes, one channel. */
private fun eorLayout(reader: ByteReader, version: Int): VagLayout {
    val sampleRate = reader.readIntLeAt(SAMPLE_RATE_OFFSET)
    val channelSize = reader.readIntLeAt(CHANNEL_SIZE_OFFSET).toLong() and UINT32_MASK
    // EoR/Maxis stores 32-char stream names in the blank SPU init frame; Killzone leaves it blank.
    val nameSize = if (version == VERSION_EOR && isPrintable(reader.get(0x30).toInt() and 0xFF)) {
        LONG_NAME_SIZE
    } else {
        NAME_SIZE
    }
    val base = VagLayout(
        channelSize = channelSize,
        sampleRate = sampleRate,
        startOffset = EOR_DATA_OFFSET,
        nameSize = nameSize,
    )
    return if (version == VERSION_EOR) base.scanned(reader) else base
}

/** Vita/PS4 `.vag` with HEVAG coding: channels live at 0x1E when the reserved fields are clean. */
private fun hevagLayout(reader: ByteReader, base: VagLayout): VagLayout {
    val channels = if (reader.readIntBeAt(0x18) == 0 &&
        (reader.readIntBeAt(0x1C).toLong() and 0xFFFF00FFL) == 0L &&
        (reader.get(0x1E).toInt() and 0xFF) < HEVAG_MAX_CHANNELS
    ) {
        (reader.get(0x1E).toInt() and 0xFF).coerceAtLeast(1)
    } else {
        1
    }
    return base.copy(channels = channels, interleave = PSX_FRAME_SIZE, channelSize = base.channelSize / channels)
        .scanned(reader)
}

/** Kingdom Hearts II (PS2): loop points are explicit header fields, not frame flags. */
private fun kh2Layout(reader: ByteReader, base: VagLayout): VagLayout? {
    val channels = reader.get(0x1E).toInt() and 0xFF
    val loopStart = reader.readIntBeAt(KH2_LOOP_START_OFFSET).toLong()
    val loopEnd = reader.readIntBeAt(KH2_LOOP_END_OFFSET).toLong()
    return if (channels > 0) {
        base.copy(
            startOffset = KH2_DATA_OFFSET,
            channels = channels,
            interleave = PSX_FRAME_SIZE,
            channelSize = base.channelSize / channels,
            loop = if (loopEnd > 0) VagLoop(loopStart, loopEnd) else null,
        )
    } else {
        null
    }
}

/**
 * Need for Speed: Hot Pursuit 2 (PS2) `.VAGx`: channel count sits at 0x2C and the interleave is
 * inferred from the gap between the last two end-marked frames.
 */
private fun vagxLayout(reader: ByteReader, base: VagLayout): VagLayout? {
    val channels = reader.readIntBeAt(VAGX_CHANNELS_OFFSET)
    val fileSize = reader.size.toLong()
    val sizeAligned = reader.size % PSX_FRAME_SIZE == 0
    val interleave = if (channels > 1) {
        detectVagxInterleave(reader, VAGX_DATA_OFFSET, fileSize) ?: 0
    } else {
        0
    }
    val valid = channels > 0 && sizeAligned && (channels == 1 || interleave > 0)
    return if (valid) {
        base.copy(
            startOffset = VAGX_DATA_OFFSET,
            channels = channels,
            interleave = interleave,
            channelSize = base.channelSize / channels,
        )
    } else {
        null
    }
}

/** Garfield: Saving Arlene (PS2): 0x800-byte header, stereo blocks with trailing empty padding. */
private fun garfieldLayout(reader: ByteReader, base: VagLayout): VagLayout {
    val padding = psFindPadding(reader, GARFIELD_DATA_OFFSET, base.channelSize, STEREO, INTERLEAVE_400)
    val channelSize = (base.channelSize - padding) / STEREO
    return base.copy(
        startOffset = GARFIELD_DATA_OFFSET,
        channels = STEREO,
        interleave = INTERLEAVE_400,
        channelSize = channelSize,
    )
}

/** Eko Software PS2 banks: 0x800-byte header, stereo, always a full-stream loop. */
private fun ekoLayout(reader: ByteReader, base: VagLayout): VagLayout? {
    val interleave = when {
        hasZeroWordAt(reader, 0x800 + INTERLEAVE_400) -> INTERLEAVE_400
        hasZeroWordAt(reader, 0x800 + INTERLEAVE_4000) -> INTERLEAVE_4000
        hasZeroWordAt(reader, 0x800 + INTERLEAVE_2000) -> INTERLEAVE_2000
        else -> 0
    }
    val channelSize = base.channelSize / STEREO
    return if (interleave > 0) {
        base.copy(
            startOffset = 0x800,
            channels = STEREO,
            interleave = interleave,
            channelSize = channelSize,
            loop = VagLoop(0, psBytesToSamples(channelSize, 1)),
        )
    } else {
        null
    }
}

/** THQ Australia: `channel_size` overshoots the file by 0x10; real data ends 0x40 bytes earlier. */
private fun thqLayout(reader: ByteReader, base: VagLayout): VagLayout =
    base.copy(channelSize = base.channelSize - THQ_CHANNEL_SIZE_HACK).scanned(reader)

/** NBA 06 (PS2) `.SKX`: stereo, interleaved at half the file, `KAudioDLL` name. */
private fun kaudioSizeMatches(reader: ByteReader, base: VagLayout): Boolean {
    val fileSize = reader.size.toLong()
    val streamSize = base.channelSize + KAUDIO_DATA_OFFSET
    return streamSize * 2 == fileSize ||
        align(streamSize, KAUDIO_ALIGN_800) * 2 == fileSize ||
        align(streamSize, INTERLEAVE_400) * 2 == fileSize
}

private fun kaudioLayout(reader: ByteReader, base: VagLayout): VagLayout =
    base.copy(channels = STEREO, interleave = (reader.size / 2)).scanned(reader, dataSize = base.channelSize)

/** Standard PS1/PS2/PS3 `.vag`: mono from 0x30, loop flags in the PS-ADPCM frames. */
private fun standardLayout(reader: ByteReader, base: VagLayout, version: Int): VagLayout {
    val numSamples = psBytesToSamples(base.channelSize, 1)
    // v2.0 files longer than 20 s often repeat the whole stream without loop flags; vgmstream then
    // accepts a repeated end frame as a full loop (Ratchet & Clank, Ecco the Dolphin).
    val detectFullLoops = version == VERSION_2_0 && numSamples > FULL_LOOP_MIN_SECONDS * base.sampleRate
    return base.scanned(reader, detectFullLoops)
}

/** `VAG1` (Metal Gear Solid 3, PS2): 0x40-byte header, optional stereo, no loop scan. */
private fun parseVag1(reader: ByteReader): VagStream? {
    val base = baseVagLayout(reader) ?: return null
    val headerChannels = reader.get(VAG1_CHANNELS_OFFSET).toInt() and 0xFF
    val layout = base.copy(
        startOffset = VAG1_DATA_OFFSET,
        channels = headerChannels.coerceAtLeast(1),
        interleave = PSX_FRAME_SIZE,
    )
    return layout.toStream(reader)
}

/** `VAG2` (Metal Gear Solid 3, PS2): 0x40-byte header, stereo interleaved every 0x800 bytes. */
private fun parseVag2(reader: ByteReader): VagStream? =
    baseVagLayout(reader)?.copy(
        startOffset = VAG2_DATA_OFFSET,
        channels = STEREO,
        interleave = INTERLEAVE_800,
    )?.toStream(reader)

/** `VAGi` (interleaved): 0x800-byte header, stereo, interleave from the little-endian field at 0x08. */
private fun parseVagi(reader: ByteReader): VagStream? =
    baseVagLayout(reader)?.copy(
        startOffset = VAGI_DATA_OFFSET,
        channels = STEREO,
        interleave = reader.readIntLeAt(VAGI_INTERLEAVE_OFFSET),
    )?.toStream(reader)

/** `pGAV` (Jak series, little endian): channel size/rate are LE at 0x0C/0x10. */
private fun parsePgav(reader: ByteReader): VagStream? {
    val channels = if (reader.hasAsciiAt(SECOND_HEADER_2000, MAGIC_PGAV)) STEREO else 1
    val interleave = when {
        reader.hasAsciiAt(SECOND_HEADER_2000, MAGIC_PGAV) -> INTERLEAVE_2000
        reader.hasAsciiAt(SECOND_HEADER_1000, MAGIC_PGAV) -> INTERLEAVE_1000
        else -> 0
    }
    val channelSize = (reader.readIntLeAt(CHANNEL_SIZE_OFFSET).toLong() and UINT32_MASK) / channels
    val layout = VagLayout(
        channelSize = channelSize,
        sampleRate = reader.readIntLeAt(SAMPLE_RATE_OFFSET),
        channels = channels,
        interleave = interleave,
    )
    return layout.toStream(reader)
}

/** `AAAp` (Acclaim Austin Audio, PS2): one 0x30-byte VAGp header per channel, then the data. */
private fun parseAaap(reader: ByteReader): VagStream? {
    val channels = reader.readShortLeAt(AAAP_CHANNELS_OFFSET).toInt() and UINT16_MASK
    val headersValid = channels > 0 &&
        (0 until channels).all { reader.hasAsciiAt(AAAP_HEADERS_OFFSET + it * VAG_HEADER_SIZE, MAGIC_VAGP) }
    val version = if (headersValid) reader.readIntBeAt(AAAP_HEADERS_OFFSET + VERSION_OFFSET) else 0
    val channelSize = if (headersValid) {
        reader.readIntBeAt(AAAP_HEADERS_OFFSET + CHANNEL_SIZE_OFFSET).toLong() and UINT32_MASK
    } else {
        0L
    }
    val sampleRate = if (headersValid) reader.readIntBeAt(AAAP_HEADERS_OFFSET + SAMPLE_RATE_OFFSET) else 0
    val dataOffset = AAAP_HEADERS_OFFSET + channels * VAG_HEADER_SIZE
    val valid = headersValid &&
        version == VERSION_2_0 &&
        sampleRate > 0 &&
        dataOffset + channelSize * channels <= reader.size
    return if (valid) {
        VagStream(sampleRate, psBytesToSamples(channelSize, 1), null, title = null)
    } else {
        null
    }
}

/** Data-first `VAGp` "footer" (Sims 2 console spinoffs): header is the last 0x40 bytes, LE. */
private fun parseFooter(reader: ByteReader): VagStream? {
    val headerOffset = reader.size - FOOTER_HEADER_SIZE
    val validHeader = headerOffset >= 0 &&
        reader.hasAsciiAt(headerOffset, MAGIC_VAGP) &&
        psCheckFormat(reader, 0, FOOTER_HEADER_SIZE)
    if (!validHeader) return null

    val version = reader.readIntLeAt(headerOffset + VERSION_OFFSET)
    val streamSize = reader.readIntLeAt(headerOffset + CHANNEL_SIZE_OFFSET).toLong() and UINT32_MASK
    val sampleRate = reader.readIntLeAt(headerOffset + SAMPLE_RATE_OFFSET)
    val sizeValid = version == VERSION_FOOTER &&
        align(streamSize + FOOTER_HEADER_SIZE, FOOTER_HEADER_SIZE) == reader.size.toLong()
    return if (sizeValid && sampleRate > 0) {
        VagStream(
            sampleRate = sampleRate,
            numSamples = psBytesToSamples(streamSize, 1),
            loop = findPsxLoop(reader, 0, streamSize, 1, 0),
            title = readName(reader, headerOffset + NAME_OFFSET, NAME_SIZE),
        )
    } else {
        null
    }
}

/** Evolution Games `.VAG` (PS2): `VAGp` erased to spaces, all fields little endian. */
private fun parseEvolution(reader: ByteReader): VagStream? {
    val version = reader.readIntLeAt(VERSION_OFFSET)
    val reservedValid = reader.hasAsciiAt(RESERVED_OFFSET, MAGIC_EVOLUTION)
    val streamSize = reader.readIntLeAt(CHANNEL_SIZE_OFFSET).toLong() and UINT32_MASK
    val declaredSize = streamSize + VAG_HEADER_SIZE
    val sizeValid = declaredSize == reader.size.toLong() || align(declaredSize, EVOLUTION_ALIGNMENT) == reader.size.toLong()
    // Upstream discards the last 0x20 bytes: the final ADPCM frame has a bogus end flag.
    val playableSize = streamSize - EVOLUTION_SIZE_HACK
    val sampleRate = if (reader.hasAsciiAt(SAMPLE_RATE_OFFSET, "tpad")) {
        BASE_SAMPLE_RATE
    } else {
        reader.readIntLeAt(SAMPLE_RATE_OFFSET)
    }
    val valid = version == 0 && reservedValid && sizeValid && playableSize > 0 && sampleRate > 0
    return if (valid) {
        VagStream(
            sampleRate = sampleRate,
            numSamples = psBytesToSamples(playableSize, 1),
            loop = null,
            title = readName(reader, NAME_OFFSET, NAME_SIZE),
        )
    } else {
        null
    }
}

/** The parse-time layout of one VAG header generation, before the shared sanity check. */
private data class VagLayout(
    val channelSize: Long,
    val sampleRate: Int,
    val startOffset: Int = STANDARD_START_OFFSET,
    val channels: Int = 1,
    val interleave: Int = 0,
    val loop: VagLoop? = null,
    val nameSize: Int = NAME_SIZE,
) {
    /** Re-runs the PS-ADPCM flag scan against this layout's data window. */
    fun scanned(
        reader: ByteReader,
        detectFullLoops: Boolean = false,
        dataSize: Long = channelSize * channels,
    ): VagLayout = copy(
        loop = findPsxLoop(reader, startOffset, dataSize, channels, interleave, detectFullLoops),
    )

    /**
     * Applies vgmstream's shared sanity bounds (`meta/vag.c:345-352`): the declared channel data
     * must fit the file, and the expected total may only exceed it by extraction padding.
     */
    fun toStream(reader: ByteReader): VagStream? {
        val fileSize = reader.size.toLong()
        val streamBytes = channelSize * channels
        val consumed = streamBytes + interleave.toLong() * channels + startOffset.toLong() * channels
        val valid = sampleRate > 0 &&
            consumed + EXTRACTION_PADDING_SLACK >= fileSize &&
            streamBytes <= fileSize
        return if (valid) {
            VagStream(
                sampleRate = sampleRate,
                numSamples = psBytesToSamples(channelSize, 1),
                loop = loop,
                title = readName(reader, NAME_OFFSET, nameSize),
            )
        } else {
            null
        }
    }
}

/**
 * Infers the `VAGx` interleave from the gap between the last two end-marked frames, scanning
 * backwards from the end of the file. Null when fewer than two end markers are found.
 */
private fun detectVagxInterleave(reader: ByteReader, startOffset: Int, fileSize: Long): Int? {
    var offset = fileSize
    var lastEnd = 0L
    var interleave = 0
    while (offset > startOffset && interleave == 0) {
        offset -= PSX_FRAME_SIZE
        val flagOffset = offset + 1
        val isEnd = flagOffset < reader.size.toLong() &&
            (reader.get(flagOffset.toInt()).toInt() and 0xFF) == FLAG_END
        if (isEnd) {
            if (lastEnd == 0L) {
                lastEnd = offset
            } else {
                interleave = (lastEnd - offset).toInt()
            }
        }
    }
    return interleave.takeIf { it > 0 }
}

/** Reverse-scans trailing all-zero PS-ADPCM frames (`ps_find_padding`), returning bytes to drop. */
private fun psFindPadding(
    reader: ByteReader,
    startOffset: Int,
    dataSize: Long,
    channels: Int,
    interleave: Int,
): Long {
    if (dataSize <= 0L || channels <= 0 || (channels > 1 && interleave <= 0)) return 0L

    var offset = startOffset + dataSize - interleave.toLong() * (channels - 1)
    var padding = 0L
    var interleaveConsumed = 0
    var scanning = true
    while (offset > startOffset && scanning) {
        offset -= PSX_FRAME_SIZE
        val readable = offset >= 0 && offset + PSX_FRAME_SIZE <= reader.size
        scanning = readable && isZeroFrame(reader, offset.toInt())
        if (scanning) {
            padding += PSX_FRAME_SIZE * channels
            interleaveConsumed += PSX_FRAME_SIZE
            if (interleaveConsumed == interleave) {
                interleaveConsumed = 0
                offset -= interleave.toLong() * (channels - 1)
            }
        }
    }
    return padding
}

/** True when the four big-endian words of the frame at [offset] are all zero. */
private fun isZeroFrame(reader: ByteReader, offset: Int): Boolean =
    reader.readIntBeAt(offset) == 0 &&
        reader.readIntBeAt(offset + 4) == 0 &&
        reader.readIntBeAt(offset + 8) == 0 &&
        reader.readIntBeAt(offset + 12) == 0

/** True when a 32-bit word at absolute [offset] exists and is zero. */
private fun hasZeroWordAt(reader: ByteReader, offset: Int): Boolean =
    offset + 4 <= reader.size && reader.readIntBeAt(offset) == 0

/** `ps_check_format`: the first frames must look like PS-ADPCM (predictor ≤ 5, flags ≤ 7). */
private fun psCheckFormat(reader: ByteReader, offset: Int, max: Int): Boolean {
    val end = minOf(offset + max, reader.size)
    var position = offset
    var valid = true
    while (position < end && valid) {
        valid = position + FRAME_HEADER_SIZE <= reader.size &&
            ((reader.get(position).toInt() and 0xFF) shr 4) <= MAX_PREDICTOR &&
            (reader.get(position + 1).toInt() and FLAG_MASK) <= MAX_FRAME_FLAG
        position += PSX_FRAME_SIZE
    }
    return valid
}

private fun isPrintable(byte: Int): Boolean = byte in MIN_PRINTABLE..MAX_PRINTABLE

/** Rounds [value] up to a multiple of [block]. */
private fun align(value: Long, block: Int): Long = (value + block - 1) / block * block

/**
 * Scans PS-ADPCM frames for their loop flags, mirroring `ps_find_loop_offsets` in
 * `coding/psx_decoder.c`: a frame flagged `0x06` marks the loop start *before* it, and one flagged
 * `0x03` marks the loop end *after* it. Both must be present for the stream to count as looping.
 * [detectFullLoops] additionally accepts a repeated end frame followed by a null frame as a full
 * stream loop (vgmstream's `ps_find_loop_offsets_full`).
 */
internal fun findPsxLoop(
    reader: ByteReader,
    startOffset: Int,
    dataSize: Long,
    channels: Int,
    interleave: Int,
    detectFullLoops: Boolean = false,
): VagLoop? {
    if (dataSize <= 0L || channels <= 0 || (channels > 1 && interleave <= 0)) return null

    val end = minOf(startOffset + dataSize, reader.size.toLong()).toInt()
    var offset = startOffset
    var numSamples = 0L
    var loopStart = 0L
    var loopEnd = 0L
    var loopStartFound = false
    var loopEndFound = false
    var interleaveConsumed = 0

    while (offset < end && offset + FRAME_HEADER_SIZE <= reader.size && !(loopStartFound && loopEndFound)) {
        val frameHeader = reader.readShortBeAt(offset).toInt() and 0xFFFF
        val flag = frameHeader and FLAG_MASK

        if (flag == FLAG_LOOP_START && !loopStartFound) {
            loopStart = numSamples
            loopStartFound = true
        }

        if (flag == FLAG_LOOP_END && !loopEndFound) {
            loopEnd = numSamples + PSX_SAMPLES_PER_FRAME
            loopEndFound = true
            // Commandos (PS2) has many loop starts/ends; a start flag right after end means ignore it.
            if (channels == 1 && offset + PSX_FRAME_SIZE < end &&
                (reader.get(offset + 1 + PSX_FRAME_SIZE).toInt() and FLAG_MASK) == FLAG_LOOP_START
            ) {
                loopEnd = 0
                loopEndFound = false
            }
        }

        if (flag == FLAG_END && detectFullLoops && isFullLoopTail(reader, offset, frameHeader)) {
            loopStart = FULL_LOOP_FIRST_FRAME_SAMPLES
            loopEnd = numSamples + PSX_SAMPLES_PER_FRAME
            loopStartFound = true
            loopEndFound = true
        }

        numSamples += PSX_SAMPLES_PER_FRAME
        offset += PSX_FRAME_SIZE
        interleaveConsumed += PSX_FRAME_SIZE
        if (interleaveConsumed == interleave) {
            interleaveConsumed = 0
            offset += interleave * (channels - 1)
        }
    }

    return if (loopStartFound && loopEndFound) VagLoop(loopStart, loopEnd) else null
}

/** True when the frame after [offset] repeats the header and is a "null end" frame (`ff 07 00…`). */
private fun isFullLoopTail(reader: ByteReader, offset: Int, frameHeader: Int): Boolean {
    val next = offset + PSX_FRAME_SIZE
    val available = next + PSX_FRAME_SIZE <= reader.size
    val first = if (available) reader.get(next).toInt() and 0xFF else -1
    val header = (frameHeader shr 8) and 0xFF
    val firstIsSilent = first == 0x00 || first == 0x0C || first == 0x3C || first == 0x1C
    val tailIsEmpty = available && (next + 2 until next + PSX_FRAME_SIZE).all { reader.get(it).toInt() == 0 }
    val matches = available && first == header && reader.get(next + 1).toInt() == 0x07
    return matches && !firstIsSilent && tailIsEmpty
}

/** PSX ADPCM is 16 bytes per frame, each frame decoding to 28 samples. */
internal fun psBytesToSamples(bytes: Long, channels: Int): Long =
    if (channels <= 0) 0L else bytes / channels / PSX_FRAME_SIZE * PSX_SAMPLES_PER_FRAME

/**
 * Play length in milliseconds for a PSX-family stream. Kept as a PSX-specific wrapper over the
 * module's shared [streamedLengthMs] so both VAG and VAB express the loop as [VagLoop].
 */
internal fun psxPlayLengthMs(numSamples: Long, sampleRate: Int, loop: VagLoop?): Long =
    streamedLengthMs(
        numSamples = numSamples,
        loopFlag = loop != null,
        loopStartSample = loop?.start ?: 0L,
        loopEndSample = loop?.end ?: 0L,
        sampleRate = sampleRate,
    )

/** NUL-terminated/space-padded Latin-1 name field; null when blank so the filename fallback runs. */
internal fun readName(reader: ByteReader, offset: Int, size: Int): String? {
    reader.position(offset)
    return reader.nextBytesAsLatin1String(size)?.takeIf { it.isNotEmpty() }
}

internal const val EXT_VAG = "vag"

private const val UINT16_MASK = 0xFFFF
private const val PSX_FRAME_SIZE = 0x10
private const val PSX_SAMPLES_PER_FRAME = 28

private const val MAGIC_VAGP = "VAGp"
private const val MAGIC_VAG1 = "VAG1"
private const val MAGIC_VAG2 = "VAG2"
private const val MAGIC_VAGI = "VAGi"
private const val MAGIC_PGAV = "pGAV"
private const val MAGIC_AAAP = "AAAp"
private const val MAGIC_EVOLUTION = "   \u0000"
private const val VERSION_OFFSET = 0x04
private const val RESERVED_OFFSET = 0x08
private const val CHANNEL_SIZE_OFFSET = 0x0C
private const val SAMPLE_RATE_OFFSET = 0x10
private const val NAME_OFFSET = 0x20
private const val NAME_SIZE = 0x10
private const val LONG_NAME_SIZE = 0x20
private const val MIN_VAG_SIZE = 0x40

private const val VERSION_2_0 = 0x00000020
private const val VERSION_EOR = 0x02000000
private const val VERSION_KILLZONE = 0x40000000
private const val VERSION_HEVAG_2_1 = 0x00020001
private const val VERSION_HEVAG_3_0 = 0x00030000
private const val VERSION_KH2 = 0x00000004
private const val VERSION_NFS = 0x00000002
private const val VERSION_FOOTER = 0x00000002
private const val RESERVED_EKO = 0x01010101
private const val HEVAG_MAX_CHANNELS = 16

private const val FULL_LOOP_MIN_SECONDS = 20
private const val FULL_LOOP_FIRST_FRAME_SAMPLES = 28L
private const val FRAME_HEADER_SIZE = 2
private const val FLAG_MASK = 0x0F
private const val FLAG_END = 0x01
private const val FLAG_LOOP_END = 0x03
private const val FLAG_LOOP_START = 0x06
private const val MAX_PREDICTOR = 5
private const val MAX_FRAME_FLAG = 7

private const val STANDARD_START_OFFSET = 0x30
private const val EXTRACTION_PADDING_SLACK = 0x200000L
private const val STEREO = 2
private const val INTERLEAVE_400 = 0x400
private const val INTERLEAVE_800 = 0x800
private const val INTERLEAVE_1000 = 0x1000
private const val INTERLEAVE_2000 = 0x2000
private const val INTERLEAVE_4000 = 0x4000
private const val INTERLEAVE_6000 = 0x6000

private const val SECOND_HEADER_800 = 0x800
private const val SECOND_HEADER_1000 = 0x1000
private const val SECOND_HEADER_2000 = 0x2000
private const val SECOND_HEADER_6000 = 0x6000

private const val EOR_DATA_OFFSET = 0x40
private const val KH2_DATA_OFFSET = 0x60
private const val KH2_LOOP_START_OFFSET = 0x14
private const val KH2_LOOP_END_OFFSET = 0x18
private const val VAGX_DATA_OFFSET = 0x30
private const val VAGX_CHANNELS_OFFSET = 0x2C
private const val GARFIELD_DATA_OFFSET = 0x800
private const val THQ_SIZE_BIAS = 0x10L
private const val THQ_CHANNEL_SIZE_HACK = 0x40L
private const val KAUDIO_DATA_OFFSET = 0x30L
private const val KAUDIO_ALIGN_800 = 0x800

private const val VAG1_DATA_OFFSET = 0x40
private const val VAG1_CHANNELS_OFFSET = 0x1E
private const val VAG2_DATA_OFFSET = 0x40
private const val VAGI_DATA_OFFSET = 0x800
private const val VAGI_INTERLEAVE_OFFSET = 0x08

private const val AAAP_CHANNELS_OFFSET = 0x06
private const val AAAP_HEADERS_OFFSET = 0x08
private const val VAG_HEADER_SIZE = 0x30
private const val FOOTER_HEADER_SIZE = 0x40

private const val EVOLUTION_ALIGNMENT = 0x80
private const val EVOLUTION_SIZE_HACK = 0x20L
private const val BASE_SAMPLE_RATE = 44_100
private const val MIN_PRINTABLE = 0x20
private const val MAX_PRINTABLE = 0x7E
