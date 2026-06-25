package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.FADE_LENGTH_MS
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

class VgmReader(private val hatchet: Hatchet) : Reader() {
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? {
        try {
            val vgm = maybeDecompress(bytes, identifier) ?: return null

            if (vgm.size < MIN_HEADER_SIZE) {
                hatchet.w("VGM parse failed: file too small (${vgm.size} bytes).")
                return null
            }

            val buf = bytesAsReader(vgm)
            val magic = buf.nextBytes(MAGIC_SIZE)
            if (magic == null || !magic.contentEquals(MAGIC_BYTES)) {
                hatchet.w("VGM parse failed: bad magic for $identifier.")
                return null
            }

            buf.position(OFFSET_GD3)
            val gd3Relative = buf.nextFourBytesAsInt()
            buf.position(OFFSET_TOTAL_SAMPLES)
            val totalSamples = buf.nextFourBytesAsInt().toLong() and UINT32_MASK
            buf.position(OFFSET_LOOP_SAMPLES)
            val loopSamples = buf.nextFourBytesAsInt().toLong() and UINT32_MASK

            val lengthMs = computeLengthMs(totalSamples, loopSamples)
            val fadeMs = if (loopSamples > 0L) FADE_LENGTH_MS else 0L
            val tag = if (gd3Relative != 0) {
                readGd3(vgm, OFFSET_GD3 + gd3Relative.toLong())
            } else {
                null
            }

            return listOf(
                RawTrack(
                    path = identifier,
                    source = "",
                    title = tag?.title.orUnknown(),
                    artist = tag?.artist.orUnknown(),
                    game = tag?.game.orUnknown(),
                    length = lengthMs,
                    trackNumber = 0,
                    fadeLengthMs = fadeMs,
                    platform = platformForSystem(tag?.system),
                    comment = tag?.notes,
                    dumper = tag?.vgmBy,
                    titleJp = tag?.titleJp,
                    artistJp = tag?.artistJp,
                    releaseDate = tag?.releaseDate,
                    gameTitleJp = tag?.gameJp,
                )
            )
        } catch (iae: IllegalArgumentException) {
            hatchet.w("VGM parse failed: illegal argument — ${iae.message}")
            return null
        } catch (e: Exception) {
            hatchet.w("VGM parse failed for $identifier: ${e.message}")
            return null
        }
    }

    private fun maybeDecompress(bytes: ByteArray, identifier: String): ByteArray? {
        if (bytes.size < 2 || bytes[0] != GZIP_MAGIC_0 || bytes[1] != GZIP_MAGIC_1) return bytes
        val decompressed = gunzip(bytes)
        if (decompressed == null) hatchet.w("VGZ decompress failed for $identifier.")
        return decompressed
    }

    private fun readGd3(bytes: ByteArray, absoluteOffset: Long): Gd3Tag? {
        if (absoluteOffset < 0 || absoluteOffset + GD3_HEADER_SIZE > bytes.size) {
            hatchet.w("VGM: GD3 offset out of bounds ($absoluteOffset).")
            return null
        }
        val buf = ByteReader.wrap(bytes)
        buf.position(absoluteOffset.toInt())

        val magic = buf.nextBytes(GD3_MAGIC_SIZE) ?: return null
        if (!magic.contentEquals(GD3_MAGIC_BYTES)) {
            hatchet.w("VGM: GD3 magic mismatch.")
            return null
        }
        buf.nextFourBytesAsInt() // version, ignored
        val payloadSize = buf.nextFourBytesAsInt().toLong() and UINT32_MASK
        val payloadStart = buf.position()
        val payloadEnd = (payloadStart + payloadSize).coerceAtMost(bytes.size.toLong()).toInt()

        val strings = ArrayList<String>(GD3_STRING_COUNT)
        var cursor = payloadStart
        while (strings.size < GD3_STRING_COUNT && cursor + 2 <= payloadEnd) {
            var end = cursor
            while (end + 2 <= payloadEnd && !(bytes[end] == 0.toByte() && bytes[end + 1] == 0.toByte())) {
                end += 2
            }
            strings.add(decodeUtf16Le(bytes, cursor, end).trim())
            cursor = end + 2
        }
        if (strings.size < GD3_STRING_COUNT) {
            hatchet.w("VGM: GD3 truncated; ${strings.size}/$GD3_STRING_COUNT strings.")
        }

        fun at(index: Int) = strings.getOrNull(index)?.takeIf { it.isNotEmpty() }
        return Gd3Tag(
            title = at(GD3_IDX_TITLE_EN),
            game = at(GD3_IDX_GAME_EN),
            artist = at(GD3_IDX_AUTHOR_EN),
            system = at(GD3_IDX_SYSTEM_EN),
            titleJp = at(GD3_IDX_TITLE_JP),
            gameJp = at(GD3_IDX_GAME_JP),
            artistJp = at(GD3_IDX_AUTHOR_JP),
            releaseDate = at(GD3_IDX_RELEASE_DATE),
            vgmBy = at(GD3_IDX_VGM_BY),
            notes = at(GD3_IDX_NOTES),
        )
    }

    // GD3 carries a free-text "system name" string; map the common values onto our platforms.
    private fun platformForSystem(system: String?): Platform {
        val name = system?.lowercase() ?: return Platform.OTHER
        return when {
            // Arcade boards first: many carry vendor names ("Sega X", "Namco System 2") that
            // would otherwise be misread as the vendor's home console.
            "arcade" in name || "cp system" in name || "cps" in name ||
                "capcom play system" in name || "neo geo" in name || "toaplan" in name ||
                "zn-1" in name || "hang-on" in name || "hang on" in name ||
                "sega model" in name || "sega x" in name || "sega y" in name ||
                "namco system" in name || "system 16" in name || "system 32" in name -> Platform.ARCADE

            // Sega CD / 32X are Genesis add-ons; bucket them with the base console.
            "mega drive" in name || "genesis" in name || "32x" in name ||
                "megacd" in name || "mega cd" in name || "mega-cd" in name ||
                "segacd" in name || "sega cd" in name -> Platform.GENESIS

            "game boy advance" in name -> Platform.GAMEBOY_ADVANCE

            "game boy" in name -> Platform.GAMEBOY

            "saturn" in name -> Platform.SATURN

            "dreamcast" in name -> Platform.DREAMCAST

            "playstation 2" in name -> Platform.PS2

            "playstation" in name -> Platform.PSX

            "nintendo 64" in name -> Platform.N64

            "nintendo ds" in name -> Platform.NDS

            "super famicom" in name || "snes" in name ||
                ("super" in name && "nintendo" in name) -> Platform.SNES

            "famicom" in name || "family computer" in name ||
                "nintendo entertainment" in name || "nes" in name -> Platform.NES

            "pc-98" in name || "pc-88" in name || "pc-80" in name || "x68000" in name ||
                "pc / dos" in name || "pc/dos" in name || "dos" in name || "msx" in name -> Platform.PC

            else -> {
                hatchet.w("VGM: unmapped GD3 system name '$system' — defaulting to OTHER.")
                Platform.OTHER
            }
        }
    }

    // Decode a UTF-16LE slice [start, end) by hand — the common stdlib has no String(bytes,charset).
    // Each little-endian 16-bit unit maps straight to a Char (surrogate pairs survive as-is).
    private fun decodeUtf16Le(bytes: ByteArray, start: Int, end: Int): String = buildString {
        var i = start
        while (i + 1 < end) {
            append(((bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)).toChar())
            i += 2
        }
    }

    private fun computeLengthMs(totalSamples: Long, loopSamples: Long): Long {
        if (totalSamples <= 0L) return LENGTH_UNKNOWN_MS
        val withExtraLoops = if (loopSamples > 0L) totalSamples + 2L * loopSamples else totalSamples
        return withExtraLoops * MILLIS_PER_SECOND / SAMPLE_RATE_HZ
    }

    companion object {
        private val MAGIC_BYTES = byteArrayOf(0x56, 0x67, 0x6d, 0x20) // "Vgm "
        private const val MAGIC_SIZE = 4
        private const val MIN_HEADER_SIZE = 0x40

        private const val OFFSET_GD3 = 0x14
        private const val OFFSET_TOTAL_SAMPLES = 0x18
        private const val OFFSET_LOOP_SAMPLES = 0x20

        private const val GZIP_MAGIC_0 = 0x1F.toByte()
        private const val GZIP_MAGIC_1 = 0x8B.toByte()

        private val GD3_MAGIC_BYTES = byteArrayOf(0x47, 0x64, 0x33, 0x20) // "Gd3 "
        private const val GD3_MAGIC_SIZE = 4
        private const val GD3_HEADER_SIZE = 12
        private const val GD3_STRING_COUNT = 11
        private const val GD3_IDX_TITLE_EN = 0
        private const val GD3_IDX_TITLE_JP = 1
        private const val GD3_IDX_GAME_EN = 2
        private const val GD3_IDX_GAME_JP = 3
        private const val GD3_IDX_SYSTEM_EN = 4
        private const val GD3_IDX_AUTHOR_EN = 6
        private const val GD3_IDX_AUTHOR_JP = 7
        private const val GD3_IDX_RELEASE_DATE = 8
        private const val GD3_IDX_VGM_BY = 9
        private const val GD3_IDX_NOTES = 10

        private const val SAMPLE_RATE_HZ = 44_100L
        private const val MILLIS_PER_SECOND = 1000L

        // Mask treating a signed 32-bit field as an unsigned value widened to Long.
        private const val UINT32_MASK = 0xFFFFFFFFL
    }
}

private data class Gd3Tag(
    val title: String?,
    val game: String?,
    val artist: String?,
    val system: String?,
    val titleJp: String? = null,
    val gameJp: String? = null,
    val artistJp: String? = null,
    val releaseDate: String? = null,
    val vgmBy: String? = null,
    val notes: String? = null,
)
