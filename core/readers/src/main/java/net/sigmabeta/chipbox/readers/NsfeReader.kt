package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import timber.log.Timber
import java.io.UnsupportedEncodingException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object NsfeReader : Reader() {
    override fun readTracksFromFile(path: String): List<RawTrack>? {
        try {
            val fileAsByteBuffer = fileAsByteBuffer(path)

            val formatHeader = fileAsByteBuffer.nextFourBytesAsString()
            if (formatHeader == null) {
                Timber.e("No header found.")
                return null
            }

            if (!isNsfeFile(formatHeader)) {
                Timber.e("NSFE header missing.")
                return null
            }

            val chunks = readNsfeChunks(fileAsByteBuffer)

            val gameMetadata = chunks.parseChunkAsStrings("auth") ?: return null

            val gameTitle = gameMetadata[0]
            val gameArtist = gameMetadata[1]

            // TODO Use the track count in the header instead of this.
            val trackNameList = chunks.parseChunkAsStrings("tlbl") ?: return null
            val artistList = chunks.parseChunkAsStrings("taut")
            val lengthChunk = chunks.parseChunkAsByteBuffer("time")
            val fadeChunk = chunks.parseChunkAsByteBuffer("fade")
            val plstChunk = chunks.parseChunkAsByteBuffer("plst")

            val lengthList = mutableListOf<Long>()
            val fadeList = mutableListOf<Long>()

            trackNameList.forEachIndexed { index, _ ->
                val length = parseTimeChunk(lengthChunk)
                val fade = parseTimeChunk(fadeChunk)

                lengthList.add(
                    index,
                    length ?: LENGTH_UNKNOWN_MS
                )

                fadeList.add(
                    index,
                    fade ?: 1L
                )
            }

            val tempTracks = mutableListOf<RawTrack>()
            val plstIndexList = plstChunk
                ?.array()
                ?.map { it.toInt() }

            trackNameList.forEachIndexed { index, name ->
                tempTracks.add(
                    RawTrack(
                        path,
                        name,
                        (artistList?.get(index) ?: gameArtist),
                        gameTitle,
                        lengthList[index],
                        fadeList[index] == 0L
                    )
                )
            }

            return plstIndexList
                ?.map { tempTracks[it] } ?: tempTracks
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
                .map { it.orValidString() }
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

                if (chunk.name == "NEND") {
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
        val name = fileAsByteBuffer.nextFourBytesAsString()
        val content = ByteArray(length)

        if (name == null) {
            Timber.e("Chunk is not well-formed.")
            return null
        }

        fileAsByteBuffer.get(content)
        return NsfeChunk(name, length, content)
    }

    private fun isNsfeFile(header: String) = header.contentEquals("NSFE")
}

data class NsfeChunk(
    val name: String,
    val length: Int,
    val content: ByteArray
)