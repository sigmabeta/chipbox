package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import timber.log.Timber
import java.io.UnsupportedEncodingException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object NsfeReader : Reader() {
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? {
        try {
            val fileAsByteBuffer = bytesAsByteBuffer(bytes)

            val formatHeader = fileAsByteBuffer.nextBytesAsString(4)
            if (formatHeader == null) {
                Timber.e("No header found.")
                return null
            }

            if (!isNsfeFile(formatHeader)) {
                Timber.e("NSFE header missing.")
                return null
            }

            val chunks = readNsfeChunks(fileAsByteBuffer)
            val gameMetadata = chunks.parseChunkAsStrings(CHUNK_AUTH) ?: return null

            val gameTitle = gameMetadata[0]
            val gameArtist = gameMetadata[1]

            // TODO Use the track count in the header instead of this.
            val trackNameList = chunks.parseChunkAsStrings(CHUNK_TLBL) ?: return null
            val artistList = chunks.parseChunkAsStrings(CHUNK_TAUT)
            val lengthChunk = chunks.parseChunkAsByteBuffer(CHUNK_TIME)
            val fadeChunk = chunks.parseChunkAsByteBuffer(CHUNK_FADE)
            val plstChunk = chunks.parseChunkAsByteBuffer(CHUNK_PLST)
            val infoChunk = chunks.parseChunkAsByteBuffer(CHUNK_INFO)

            val lengthList = mutableListOf<Long>()
            val fadeList = mutableListOf<Long>()

            val trackCount = infoChunk?.get(0x08)?.toInt()?.and(0xFF) ?: trackNameList.size

            for (index in 0 until trackCount) {
                val length = parseTimeChunk(lengthChunk)
                val fade = parseTimeChunk(fadeChunk)

                lengthList.add(
                    length ?: LENGTH_UNKNOWN_MS
                )

                fadeList.add(
                    fade ?: 1L
                )
            }

            val tempTracks = mutableListOf<RawTrack>()
            val plstIndexList = plstChunk
                ?.array()
                ?.map { it.toInt() and 0xFF }

            // tempTracks is indexed by subtune number, matching tlbl and time chunks
            trackNameList.take(trackCount).forEachIndexed { index, name ->
                tempTracks.add(
                    RawTrack(
                        identifier,
                        "",
                        name,
                        (artistList?.get(index) ?: gameArtist),
                        gameTitle,
                        lengthList[index],
                        index,
                        fadeList[index] == 0L
                    )
                )
            }

            // GME's start_track_(N) remaps N via playlist[N] internally, so trackNumber
            // must be the playlist position, not the subtune index.
            return if (plstIndexList != null) {
                plstIndexList.mapIndexed { playlistPos, subtuneIndex ->
                    tempTracks[subtuneIndex].copy(trackNumber = playlistPos)
                }
            } else {
                tempTracks
            }
        } catch (iae: IllegalArgumentException) {
            Timber.e("Illegal argument: ${iae.message}")
            return null
        } catch (e: UnsupportedEncodingException) {
            Timber.e("Unsupported Encoding: ${e.message}")
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
                Timber.e("Buffer underflow reading chunk.")
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
            Timber.e("Chunk is not well-formed.")
            return null
        }

        fileAsByteBuffer.get(content)
        return NsfeChunk(name, length, content)
    }

    private fun isNsfeFile(header: String) = header.contentEquals(HEADER_MAGIC)

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

data class NsfeChunk(
    val name: String,
    val length: Int,
    val content: ByteArray
)