package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet
import java.io.UnsupportedEncodingException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NsfeReader(private val hatchet: Hatchet) : Reader() {
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? {
        try {
            val fileAsByteBuffer = bytesAsByteBuffer(bytes)

            val formatHeader = fileAsByteBuffer.nextBytesAsString(4)
            if (formatHeader == null) {
                hatchet.w("NSFE parse failed: file too small to contain header (${bytes.size} bytes).")
                return null
            }

            if (!isNsfeFile(formatHeader)) {
                hatchet.w("NSFE parse failed: header doesn't start with 'NSFE' (got '$formatHeader').")
                return null
            }

            val chunks = readNsfeChunks(fileAsByteBuffer)
            val gameMetadata = chunks.parseChunkAsStrings(CHUNK_AUTH)
            val gameTitle = gameMetadata?.getOrNull(0).orUnknown()
            val gameArtist = gameMetadata?.getOrNull(1).orUnknown()

            val trackNameList = chunks.parseChunkAsStrings(CHUNK_TLBL).orEmpty()
            val artistList = chunks.parseChunkAsStrings(CHUNK_TAUT)
            val lengthChunk = chunks.parseChunkAsByteBuffer(CHUNK_TIME)
            val fadeChunk = chunks.parseChunkAsByteBuffer(CHUNK_FADE)
            val plstChunk = chunks.parseChunkAsByteBuffer(CHUNK_PLST)
            val infoChunk = chunks.parseChunkAsByteBuffer(CHUNK_INFO)

            // INFO[8] is the canonical track count per spec; fall back to tlbl length only if
            // the file omits INFO. Bail when neither is available — there's nothing to scan.
            val trackCount = infoChunk?.get(0x08)?.toInt()?.and(0xFF)
                ?: trackNameList.size.takeIf { it > 0 }
                ?: return null

            val tempTracks = mutableListOf<RawTrack>()
            for (index in 0 until trackCount) {
                val length = parseTimeChunk(lengthChunk) ?: LENGTH_UNKNOWN_MS
                // NSFE 'fade' chunk holds one fade-out duration (ms) per subtune. >0 = fade,
                // 0 = no fade; a missing chunk means no per-track fade metadata at all.
                val fadeMs = parseTimeChunk(fadeChunk) ?: 0L

                tempTracks.add(
                    RawTrack(
                        identifier,
                        "",
                        trackNameList.getOrNull(index) ?: TAG_UNKNOWN,
                        artistList?.getOrNull(index) ?: gameArtist,
                        gameTitle,
                        length,
                        index,
                        fadeMs.coerceAtLeast(0L),
                        platform = Platform.NES,
                    )
                )
            }

            // GME's start_track_(N) remaps N via playlist[N] internally, so trackNumber
            // must be the playlist position, not the subtune index.
            val plstIndexList = plstChunk?.array()?.map { it.toInt() and 0xFF }
            return if (plstIndexList != null) {
                plstIndexList.mapIndexedNotNull { playlistPos, subtuneIndex ->
                    tempTracks.getOrNull(subtuneIndex)?.copy(trackNumber = playlistPos)
                }
            } else {
                tempTracks
            }
        } catch (iae: IllegalArgumentException) {
            hatchet.w("NSFE parse failed: illegal argument — ${iae.message}")
            return null
        } catch (e: UnsupportedEncodingException) {
            hatchet.w("NSFE parse failed: unsupported encoding — ${e.message}")
            return null
        }
    }

    private fun parseTimeChunk(
        chunk: ByteBuffer?
    ) = try {
        chunk?.nextFourBytesAsInt()?.toLong()
    } catch (ex: BufferUnderflowException) {
        null
    }

    private fun List<NsfeChunk>.parseChunkAsStrings(chunkName: String): List<String>? {
        return try {
            first { it.name == chunkName }
                .content
                .toString(Charsets.UTF_8)
                .split(0.toChar())
                .map { it.trim() }
                .map { it.orUnknown() }
        } catch (ex: NoSuchElementException) {
            null
        }
    }

    private fun List<NsfeChunk>.parseChunkAsByteBuffer(chunkName: String): ByteBuffer? {
        return try {
            val chunk = first { it.name == chunkName }
            chunk
                .content
                .let { ByteBuffer.wrap(it, 0, chunk.length) }
                .order(ByteOrder.LITTLE_ENDIAN)
        } catch (ex: NoSuchElementException) {
            null
        }
    }

    private fun readNsfeChunks(fileAsByteBuffer: ByteBuffer): List<NsfeChunk> {
        val chunks = mutableListOf<NsfeChunk>()
        while (true) {
            try {
                val chunk = readNextChunk(fileAsByteBuffer) ?: continue

                chunks.add(chunk)

                if (chunk.name == CHUNK_NEND) {
                    break
                }
            } catch (ex: BufferUnderflowException) {
                hatchet.w("NSFE parse failed: buffer underflow reading chunk.")
                return chunks
            }
        }
        return chunks
    }

    private fun readNextChunk(fileAsByteBuffer: ByteBuffer): NsfeChunk? {
        val length = fileAsByteBuffer.nextFourBytesAsInt()
        val name = fileAsByteBuffer.nextBytesAsString(4)
        val content = ByteArray(length)

        if (name == null) {
            hatchet.w("NSFE parse failed: chunk is not well-formed.")
            return null
        }

        fileAsByteBuffer.get(content)
        return NsfeChunk(name, length, content)
    }

    private fun isNsfeFile(header: String) = header.contentEquals(HEADER_MAGIC)

    companion object {
        private const val HEADER_MAGIC = "NSFE"
        private const val CHUNK_AUTH = "auth"
        private const val CHUNK_TLBL = "tlbl"
        private const val CHUNK_TAUT = "taut"
        private const val CHUNK_TIME = "time"
        private const val CHUNK_FADE = "fade"
        private const val CHUNK_PLST = "plst"
        private const val CHUNK_INFO = "INFO"
        private const val CHUNK_NEND = "NEND"
    }
}

data class NsfeChunk(
    val name: String,
    val length: Int,
    val content: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NsfeChunk

        if (length != other.length) return false
        if (name != other.name) return false
        if (!content.contentEquals(other.content)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = length
        result = 31 * result + name.hashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}
