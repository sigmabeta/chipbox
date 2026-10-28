package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * PlayStation / CD-i CD-XA audio (`.xa`): raw 2352-byte mode2/form2 CD sectors, or the same data
 * behind the RIFF/CDXA wrapper a CD driver adds when copying a track out. Port of vgmstream's
 * `meta/xa.c` (magic check, `xa_read_subsongs`, coding-header parse) and `coding/xa_decoder.c`
 * (`xa_bytes_to_samples`); XA streams never loop, so the play length is the sample count.
 * Upstream's `xa_check_format` ADPCM-frame sniffing is not ported: the magic word plus the
 * per-sector coding header are the acceptance test here.
 *
 * XA sectors interleave several "file + channel" streams (the CD drive plays one at a time). A new
 * subsong starts whenever a channel's file/channel/coding marker changes, and the sector's
 * end-of-file submode bit closes the current one, so the same marker may reappear as a later
 * subsong. Silent padding sectors use channel 0xFF and non-audio submodes (video/data) are ignored.
 *
 * Length is computed from the sector count of each subsong, exactly like upstream:
 * `(bytes / 0x930) * (28 * subframes / channels) * (form2 ? 18 : 16)` samples, then
 * `samples * 1000 / rate` with integer truncation (the native probe's formula).
 */
class XaReader(private val hatchet: Hatchet) : Reader() {
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        try {
            val start = blockedAudioStart(bytes)
            if (start == null) {
                hatchet.w("XA parse failed: no raw mode2 sync word or RIFF/CDXA header.")
                null
            } else {
                readBlockedTracks(bytes, start, identifier)
            }
        } catch (e: IllegalArgumentException) {
            hatchet.w("XA parse failed: ${e.message}.")
            null
        }

    /**
     * Rejects a stream whose last sector is incomplete: a partial sector cannot be length-counted
     * without inventing samples. Everything else is delegated to the sector walk.
     */
    private fun readBlockedTracks(
        bytes: ByteArray,
        start: Int,
        identifier: String,
    ): List<RawTrack>? {
        val unalignedBytes = (bytes.size - start) % SECTOR_SIZE
        return if (unalignedBytes != 0) {
            // Upstream would read past the truncation (a short final sector still counts), which
            // silently inflates that subsong's length. Reject it instead of mis-lengthening a track.
            hatchet.w("XA parse failed: $unalignedBytes trailing byte(s) do not form a full $SECTOR_SIZE-byte sector.")
            null
        } else {
            tracksFromSubsongs(bytes, start, identifier)
        }
    }

    private fun tracksFromSubsongs(
        bytes: ByteArray,
        start: Int,
        identifier: String,
    ): List<RawTrack>? {
        val reader = bytesAsReader(bytes)
        val subsongs = walkSubsongs(reader, start)
        return if (subsongs == null) {
            hatchet.w("XA parse failed: sector channel marker above ${CHANNEL_LIMIT - 1}.")
            null
        } else {
            if (subsongs.isEmpty()) hatchet.d("XA: parsed a valid sector stream but found no audio.")
            subsongs.mapIndexed { index, subsong ->
                trackFor(subsongs.size, index, subsong, reader, identifier)
            }
        }
    }

    private fun trackFor(
        subsongCount: Int,
        index: Int,
        subsong: Subsong,
        reader: ByteReader,
        identifier: String,
    ): RawTrack {
        val info = reader.readIntBeAt(subsong.firstOffset + SUBHEADER_OFFSET)
        val header = requireNotNull(headerFor(info)) {
            "unsupported coding header 0x${(info and BYTE_MASK).toString(16)} " +
                "in the sector at ${subsong.firstOffset}"
        }
        return RawTrack(
            path = identifier,
            source = "",
            title = titleFor(subsongCount, subsong.config),
            artist = TAG_UNKNOWN,
            game = TAG_UNKNOWN,
            length = lengthMs(header, subsong.sectorCount),
            trackNumber = subsongTrackNumber(index),
            fadeLengthMs = 0L,
            platform = Platform.PSX,
        )
    }

    /**
     * Walks every full sector, grouping audio sectors into subsongs by their (file+channel+coding)
     * marker and resetting a channel after its end-of-file sector. Mirrors `xa_read_subsongs`:
     * a marker of 0 is upstream's "unassigned" state, so those sectors belong to no subsong.
     * Returns null for a channel marker above the 128 upstream supports (it fails the whole file);
     * a file with no audio sectors yields an empty list, which is not a failure.
     */
    private fun walkSubsongs(reader: ByteReader, start: Int): List<Subsong>? {
        val lastMarker = IntArray(CHANNEL_LIMIT)
        val currentSubsong = IntArray(CHANNEL_LIMIT)
        val subsongs = mutableListOf<Subsong>()
        var offset = start
        while (offset + SECTOR_SIZE <= reader.size) {
            val info = reader.readIntBeAt(offset + SUBHEADER_OFFSET)
            val channel = (info ushr CHANNEL_SHIFT) and BYTE_MASK
            val submode = (info ushr SUBMODE_SHIFT) and BYTE_MASK
            when {
                channel == PADDING_CHANNEL -> Unit

                channel >= CHANNEL_LIMIT -> return null

                !isAudioSubmode(submode) -> Unit

                else -> {
                    if (lastMarker[channel] != (info and GROUP_MARKER_MASK)) {
                        lastMarker[channel] = info and GROUP_MARKER_MASK
                        currentSubsong[channel] = subsongs.size + 1
                        subsongs += Subsong(offset, (info ushr CHANNEL_SHIFT) and CONFIG_MASK)
                    }
                    subsongs.getOrNull(currentSubsong[channel] - 1)?.let { it.sectorCount++ }
                    if ((submode and SUBMODE_EOF) != 0) {
                        lastMarker[channel] = 0
                        currentSubsong[channel] = 0
                    }
                }
            }
            offset += SECTOR_SIZE
        }
        return subsongs
    }

    /** The stream name upstream exposes for a multi-subsong file, else the unknown sentinel. */
    private fun titleFor(subsongCount: Int, config: Int): String =
        if (subsongCount > 1) config.toString(16).padStart(CONFIG_HEX_WIDTH, '0') else TAG_UNKNOWN

    private fun lengthMs(header: XaHeader, sectorCount: Int): Long {
        val subframes = if (header.bitsPerSample == BITS_8) SUBFRAMES_8_BIT else SUBFRAMES_4_BIT
        val samplesPerSector = SAMPLES_PER_FRAME * subframes / header.channels
        val framesPerSector = if (header.isForm2) FORM2_FRAMES else FORM1_FRAMES
        val samples = sectorCount.toLong() * samplesPerSector * framesPerSector
        return samples * MILLIS_PER_SECOND / header.sampleRate
    }

    /**
     * Decodes the sector's coding-info byte, accepting exactly what upstream accepts: mono/stereo,
     * 37.8/18.9 kHz and 4/8-bit, except 8-bit mono which upstream rejects as untested.
     */
    private fun headerFor(info: Int): XaHeader? {
        val codingInfo = info and BYTE_MASK
        val channelCode = codingInfo and CODING_CHANNELS_MASK
        val rateCode = (codingInfo ushr CODING_RATE_SHIFT) and CODING_RATE_MASK
        val bpsCode = (codingInfo ushr CODING_BPS_SHIFT) and CODING_BPS_MASK
        val unsupportedCode = channelCode > 1 || rateCode > 1 || bpsCode > 1
        val unsupported8BitMono = bpsCode == BPS_8_CODE && channelCode == MONO_CODE
        return if (unsupportedCode || unsupported8BitMono) {
            null
        } else {
            XaHeader(
                channels = channelCode + 1,
                sampleRate = if (rateCode == 0) RATE_37800 else RATE_18900,
                bitsPerSample = if (bpsCode == 0) BITS_4 else BITS_8,
                isForm2 = ((info ushr SUBMODE_SHIFT) and SUBMODE_FORM2) != 0,
            )
        }
    }

    /** Offset of the first CD sector: 0 for raw rips, after the RIFF/CDXA header otherwise. */
    private fun blockedAudioStart(bytes: ByteArray): Int? = when {
        hasRawSyncWord(bytes) -> RAW_START_OFFSET
        hasRiffCdXaHeader(bytes) -> RIFF_START_OFFSET
        else -> null
    }

    /** Raw CD-XA sync + header: `00 FF FF FF FF FF FF FF FF FF FF 00`, i.e. three magic words. */
    private fun hasRawSyncWord(bytes: ByteArray): Boolean {
        if (bytes.size < RAW_SYNC_SIZE) return false
        val reader = bytesAsReader(bytes)
        return reader.readIntBeAt(RAW_SYNC_OFFSET_1) == RAW_SYNC_WORD_1 &&
            reader.readIntBeAt(RAW_SYNC_OFFSET_2) == RAW_SYNC_WORD_2 &&
            reader.readIntBeAt(RAW_SYNC_OFFSET_3) == RAW_SYNC_WORD_3
    }

    private fun hasRiffCdXaHeader(bytes: ByteArray): Boolean =
        bytes.hasAsciiAt(RIFF_MAGIC_OFFSET, RIFF_MAGIC) &&
            bytes.hasAsciiAt(CDXA_MAGIC_OFFSET, CDXA_MAGIC) &&
            bytes.hasAsciiAt(FMT_MAGIC_OFFSET, FMT_MAGIC)

    private fun isAudioSubmode(submode: Int): Boolean =
        (submode and SUBMODE_AUDIO) != 0 &&
            (submode and SUBMODE_DATA) == 0 &&
            (submode and SUBMODE_VIDEO) == 0

    /** One file+channel stream: its first sector (for the coding header) and its sector count. */
    private class Subsong(val firstOffset: Int, val config: Int) {
        var sectorCount: Int = 0
    }

    private data class XaHeader(
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val isForm2: Boolean,
    )

    companion object {
        private const val SECTOR_SIZE = 0x930
        private const val SUBHEADER_OFFSET = 0x10
        private const val CHANNEL_LIMIT = 128
        private const val PADDING_CHANNEL = 0xFF
        private const val CONFIG_HEX_WIDTH = 4

        private const val BYTE_MASK = 0xFF
        private const val CONFIG_MASK = 0xFFFF
        private const val GROUP_MARKER_MASK = 0xFFFF00FF.toInt()
        private const val CHANNEL_SHIFT = 16
        private const val SUBMODE_SHIFT = 8

        private const val SUBMODE_VIDEO = 0x02
        private const val SUBMODE_AUDIO = 0x04
        private const val SUBMODE_DATA = 0x08
        private const val SUBMODE_FORM2 = 0x20
        private const val SUBMODE_EOF = 0x80

        private const val CODING_CHANNELS_MASK = 0x03
        private const val CODING_RATE_SHIFT = 2
        private const val CODING_RATE_MASK = 0x03
        private const val CODING_BPS_SHIFT = 4
        private const val CODING_BPS_MASK = 0x03
        private const val MONO_CODE = 0
        private const val BPS_8_CODE = 1

        private const val RATE_37800 = 37_800
        private const val RATE_18900 = 18_900
        private const val BITS_4 = 4
        private const val BITS_8 = 8
        private const val SUBFRAMES_4_BIT = 8
        private const val SUBFRAMES_8_BIT = 4
        private const val SAMPLES_PER_FRAME = 28
        private const val FORM1_FRAMES = 16
        private const val FORM2_FRAMES = 18
        private const val MILLIS_PER_SECOND = 1000L

        private const val RAW_START_OFFSET = 0x00
        private const val RIFF_START_OFFSET = 0x2C
        private const val RAW_SYNC_SIZE = 12
        private const val RAW_SYNC_OFFSET_1 = 0x00
        private const val RAW_SYNC_OFFSET_2 = 0x04
        private const val RAW_SYNC_OFFSET_3 = 0x08
        private const val RAW_SYNC_WORD_1 = 0x00FFFFFF
        private const val RAW_SYNC_WORD_2 = -1 // 0xFFFFFFFF
        private const val RAW_SYNC_WORD_3 = 0xFFFFFF00.toInt()
        private const val RIFF_MAGIC_OFFSET = 0x00
        private const val CDXA_MAGIC_OFFSET = 0x08
        private const val FMT_MAGIC_OFFSET = 0x0C
        private const val RIFF_MAGIC = "RIFF"
        private const val CDXA_MAGIC = "CDXA"
        private const val FMT_MAGIC = "fmt "
    }
}
