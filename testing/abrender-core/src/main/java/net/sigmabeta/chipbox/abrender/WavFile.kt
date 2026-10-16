package net.sigmabeta.chipbox.abrender

import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Streaming writer for a canonical 16-bit stereo PCM WAV — the listenable artifact for each rendered
 * track. The RIFF/`data` chunk sizes aren't known until the end, so they're back-patched in [close].
 * Sample format matches [SignalStats]' hash and the player's own cache: interleaved L/R s16le.
 */
@Suppress("MagicNumber") // RIFF/WAVE byte offsets, masks, and shift widths are the format spec
class WavWriter(private val file: File, private val sampleRate: Int) {
    private val out = BufferedOutputStream(file.outputStream())
    private var dataBytes = 0L

    init {
        file.parentFile?.mkdirs()
        out.write(headerBytes(sampleRate, dataBytes = 0))
    }

    /** Writes the first [frames] stereo frames of [buffer] (interleaved L/R). */
    fun write(buffer: ShortArray, frames: Int) {
        val samples = frames * 2
        val bytes = ByteArray(samples * 2)
        var b = 0
        var i = 0
        while (i < samples) {
            val s = buffer[i].toInt()
            bytes[b++] = (s and 0xFF).toByte()
            bytes[b++] = ((s shr 8) and 0xFF).toByte()
            i++
        }
        out.write(bytes)
        dataBytes += bytes.size
    }

    fun close() {
        out.flush()
        out.close()
        // Back-patch RIFF size (offset 4) and data size (offset 40) now that the totals are known.
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(intLe((36 + dataBytes).toInt()))
            raf.seek(40)
            raf.write(intLe(dataBytes.toInt()))
        }
    }

    private companion object {
        fun headerBytes(sampleRate: Int, dataBytes: Int): ByteArray {
            val byteRate = sampleRate * CHANNELS * BYTES_PER_SAMPLE
            return "RIFF".toByteArray() + intLe(36 + dataBytes) + "WAVE".toByteArray() +
                "fmt ".toByteArray() + intLe(16) + shortLe(1) + shortLe(CHANNELS) +
                intLe(sampleRate) + intLe(byteRate) +
                shortLe(CHANNELS * BYTES_PER_SAMPLE) + shortLe(16) +
                "data".toByteArray() + intLe(dataBytes)
        }

        const val CHANNELS = 2
        const val BYTES_PER_SAMPLE = 2

        fun intLe(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte(),
        )

        fun shortLe(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    }
}

/** Decoded PCM body of a WAV, used by `diff` for sample-by-sample comparison. */
class WavPcm(val sampleRate: Int, val samples: ShortArray)

/**
 * Minimal RIFF/WAVE reader for the subset [WavWriter] produces (16-bit PCM). Walks the chunk list to
 * find `fmt ` and `data` rather than assuming a fixed 44-byte header, so it also tolerates WAVs with
 * extra chunks.
 */
@Suppress("MagicNumber", "ReturnCount") // RIFF/WAVE chunk offsets/widths; guard returns per chunk
fun readWavPcm(file: File): WavPcm? {
    val bytes = file.readBytes()
    if (bytes.size < 12 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null

    var sampleRate = 0
    var pos = 12
    while (pos + 8 <= bytes.size) {
        val id = String(bytes, pos, 4)
        val size = intLe(bytes, pos + 4)
        val body = pos + 8
        when (id) {
            "fmt " -> sampleRate = intLe(bytes, body + 4)

            "data" -> {
                val count = minOf(size, bytes.size - body) / 2
                val samples = ShortArray(count)
                var i = 0
                while (i < count) {
                    val lo = bytes[body + i * 2].toInt() and 0xFF
                    val hi = bytes[body + i * 2 + 1].toInt()
                    samples[i] = ((hi shl 8) or lo).toShort()
                    i++
                }
                return WavPcm(sampleRate, samples)
            }
        }
        pos = body + size + (size and 1) // chunks are word-aligned
    }
    return null
}

@Suppress("MagicNumber") // little-endian byte assembly
private fun intLe(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xFF) or
        ((b[off + 1].toInt() and 0xFF) shl 8) or
        ((b[off + 2].toInt() and 0xFF) shl 16) or
        ((b[off + 3].toInt() and 0xFF) shl 24)
