package net.sigmabeta.chipbox.player.cache.real

import kotlin.concurrent.Volatile
import net.sigmabeta.chipbox.player.cache.PcmCacheFormat
import net.sigmabeta.chipbox.player.cache.PcmCacheKey
import okio.Buffer
import okio.FileHandle
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Read/write handle to a single `.pcm` cache file, backed by an injected [FileSystem] so the
 * format logic is platform-neutral.
 *
 * Writers append samples to the body, then call [Writer.complete] to flip the header's
 * completion flag and fsync. Readers verify the header is well-formed and complete before any
 * read; otherwise [openForRead] returns null and the caller should treat the entry as a miss.
 *
 * Writers use a `.pcm.tmp` filename and atomically rename to `.pcm` on completion so that any
 * crash or kill mid-write leaves the cache directory cleanly diagnosable: a stray `.pcm.tmp`
 * means an interrupted write and is sweepable; a `.pcm` is by construction complete.
 */
internal object PcmCacheFile {

    fun openForWrite(
        fileSystem: FileSystem,
        cacheDir: Path,
        key: PcmCacheKey,
        trackId: Long,
        trackLengthMs: Long,
    ): Writer {
        fileSystem.createDirectories(cacheDir)
        val tempPath = cacheDir / key.tempFilename()
        val finalPath = cacheDir / key.filename()

        // Discard any prior interrupted write — we're starting fresh.
        fileSystem.delete(tempPath, mustExist = false)

        val handle = fileSystem.openReadWrite(tempPath)
        try {
            writeHeader(
                handle = handle,
                sampleRate = key.sampleRate,
                frameCount = 0L,
                completion = PcmCacheFormat.COMPLETION_IN_PROGRESS,
                sourceHash = key.sourceHash,
                trackId = trackId,
                trackNumber = key.trackNumber,
                trackLengthMs = trackLengthMs,
                integratedLufs = Double.NaN,
                truePeakDbtp = Double.NEGATIVE_INFINITY,
            )
        } catch (t: Throwable) {
            handle.close()
            fileSystem.delete(tempPath, mustExist = false)
            throw t
        }

        return Writer(fileSystem, handle, tempPath, finalPath, key)
    }

    fun openForRead(fileSystem: FileSystem, cacheDir: Path, key: PcmCacheKey): Reader? {
        val finalPath = cacheDir / key.filename()
        if (fileSystem.metadataOrNull(finalPath)?.isRegularFile != true) return null

        return try {
            val handle = fileSystem.openReadOnly(finalPath)
            val header = readHeader(handle)
            if (header == null || header.completion != PcmCacheFormat.COMPLETION_COMPLETE) {
                handle.close()
                null
            } else if (header.sampleRate != key.sampleRate ||
                header.sourceHash != key.sourceHash ||
                header.trackNumber != key.trackNumber
            ) {
                // Stale file — key collision is theoretically possible if the filename
                // scheme ever changes; refuse rather than serve wrong audio.
                handle.close()
                null
            } else {
                Reader(handle, finalPath, header)
            }
        } catch (e: IOException) {
            null
        }
    }

    fun readHeader(fileSystem: FileSystem, path: Path): Header? = try {
        val handle = fileSystem.openReadOnly(path)
        try {
            readHeader(handle)
        } finally {
            handle.close()
        }
    } catch (e: IOException) {
        null
    }

    private fun readHeader(handle: FileHandle): Header? {
        if (handle.size() < PcmCacheFormat.HEADER_SIZE_BYTES) return null

        val headerBytes = ByteArray(PcmCacheFormat.HEADER_SIZE_BYTES)
        var read = 0
        while (read < headerBytes.size) {
            val n = handle.read(read.toLong(), headerBytes, read, headerBytes.size - read)
            if (n < 0) return null
            read += n
        }
        val buf = Buffer().apply { write(headerBytes) }

        val magic = buf.readIntLe()
        if (magic != PcmCacheFormat.MAGIC) return null
        val version = buf.readIntLe()
        if (version != PcmCacheFormat.VERSION) return null
        val sampleRate = buf.readIntLe()
        val channels = buf.readShortLe()
        val bitsPerSample = buf.readShortLe()
        if (channels != PcmCacheFormat.CHANNELS || bitsPerSample != PcmCacheFormat.BITS_PER_SAMPLE) {
            return null
        }
        val frameCount = buf.readLongLe()
        val completion = buf.readIntLe()
        val sourceHashBytes = buf.readByteArray(PcmCacheFormat.SOURCE_HASH_FIELD_BYTES.toLong())
        val trackId = buf.readLongLe()
        val trackNumber = buf.readIntLe()
        val trackLengthMs = buf.readLongLe()
        // Loudness fields live in the reserved region. A file written before they existed
        // reads back values outside the valid LUFS range (always <= 0), so [normalizationGain]
        // rejects them and leaves audio un-normalized.
        val integratedLufs = Double.fromBits(buf.readLongLe())
        val truePeakDbtp = Double.fromBits(buf.readLongLe())
        // Remaining bytes are reserved; ignore.

        return Header(
            sampleRate = sampleRate,
            frameCount = frameCount,
            completion = completion,
            sourceHash = sourceHashBytes.decodeToString().trimEnd(' '),
            trackId = trackId,
            trackNumber = trackNumber,
            trackLengthMs = trackLengthMs,
            integratedLufs = integratedLufs,
            truePeakDbtp = truePeakDbtp,
        )
    }

    @Suppress("LongParameterList")
    private fun writeHeader(
        handle: FileHandle,
        sampleRate: Int,
        frameCount: Long,
        completion: Int,
        sourceHash: String,
        trackId: Long,
        trackNumber: Int,
        trackLengthMs: Long,
        integratedLufs: Double,
        truePeakDbtp: Double,
    ) {
        val buf = Buffer()
        buf.writeIntLe(PcmCacheFormat.MAGIC)
        buf.writeIntLe(PcmCacheFormat.VERSION)
        buf.writeIntLe(sampleRate)
        buf.writeShortLe(PcmCacheFormat.CHANNELS.toInt())
        buf.writeShortLe(PcmCacheFormat.BITS_PER_SAMPLE.toInt())
        buf.writeLongLe(frameCount)
        buf.writeIntLe(completion)
        val hashBytes = sourceHash.encodeToByteArray()
        val padded = ByteArray(PcmCacheFormat.SOURCE_HASH_FIELD_BYTES)
        hashBytes.copyInto(padded, 0, 0, minOf(hashBytes.size, padded.size))
        buf.write(padded)
        buf.writeLongLe(trackId)
        buf.writeIntLe(trackNumber)
        buf.writeLongLe(trackLengthMs)
        buf.writeLongLe(integratedLufs.toRawBits())
        buf.writeLongLe(truePeakDbtp.toRawBits())
        // Pad reserved bytes to fill the fixed header.
        repeat((PcmCacheFormat.HEADER_SIZE_BYTES - buf.size).toInt()) { buf.writeByte(0) }

        val headerBytes = buf.readByteArray()
        handle.write(0L, headerBytes, 0, headerBytes.size)
    }

    data class Header(
        val sampleRate: Int,
        val frameCount: Long,
        val completion: Int,
        val sourceHash: String,
        val trackId: Long,
        val trackNumber: Int,
        val trackLengthMs: Long,
        val integratedLufs: Double,
        val truePeakDbtp: Double,
    )

    /**
     * Append-only writer for a `.pcm.tmp` file. Tracks frames written. [complete] rewrites the
     * header with the final frame count and the complete flag and closes the write handle, but does
     * NOT rename — the rename is deferred to [promote] because a render-ahead reader keeps a handle
     * open on the temp file until playback ends, and Windows refuses to rename a file with any open
     * handle (POSIX tolerates it).
     */
    internal class Writer(
        private val fileSystem: FileSystem,
        private val handle: FileHandle,
        val tempPath: Path,
        val finalPath: Path,
        private val key: PcmCacheKey,
    ) {
        @Volatile
        private var framesWrittenInternal: Long = 0L

        @Volatile
        private var closed = false

        @Volatile
        private var completed = false

        @Volatile
        private var promoted = false

        val framesWritten: Long get() = framesWrittenInternal

        fun appendFrames(buffer: ShortArray, framesToWrite: Int) {
            if (closed || framesToWrite <= 0) return
            val shortCount = framesToWrite * 2
            val byteCount = framesToWrite * PcmCacheFormat.BYTES_PER_FRAME
            val bytes = ByteArray(byteCount)
            for (i in 0 until shortCount) {
                val sample = buffer[i].toInt()
                bytes[i * 2] = (sample and 0xFF).toByte()
                bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
            }
            val offset = PcmCacheFormat.HEADER_SIZE_BYTES.toLong() +
                framesWrittenInternal * PcmCacheFormat.BYTES_PER_FRAME
            handle.write(offset, bytes, 0, byteCount)
            framesWrittenInternal += framesToWrite.toLong()
        }

        fun complete(
            trackId: Long,
            trackLengthMs: Long,
            integratedLufs: Double,
            truePeakDbtp: Double,
        ) {
            if (closed) return
            try {
                writeHeader(
                    handle = handle,
                    sampleRate = key.sampleRate,
                    frameCount = framesWrittenInternal,
                    completion = PcmCacheFormat.COMPLETION_COMPLETE,
                    sourceHash = key.sourceHash,
                    trackId = trackId,
                    trackNumber = key.trackNumber,
                    trackLengthMs = trackLengthMs,
                    integratedLufs = integratedLufs,
                    truePeakDbtp = truePeakDbtp,
                )
                handle.flush()
            } finally {
                closed = true
                handle.close()
            }
            completed = true
        }

        /**
         * Rename the sealed temp file to its final `.pcm` name. The rename is split out of [complete]
         * and deferred to here because the render-ahead reader ([CachingPcmSource]) keeps a handle
         * open on the temp file until playback of this track ends, and Windows fails a rename while
         * any handle is open. Call only once every other handle on the temp file is closed (POSIX
         * would tolerate an earlier rename; doing it in one place keeps both platforms identical).
         * No-op unless [complete] succeeded; idempotent.
         */
        fun promote() {
            if (!completed || promoted) return
            promoted = true
            // If the destination already exists (a concurrent writer won), keep the older one.
            if (!fileSystem.exists(finalPath)) {
                fileSystem.atomicMove(tempPath, finalPath)
            } else {
                fileSystem.delete(tempPath, mustExist = false)
            }
        }

        fun abort() {
            if (closed) return
            closed = true
            try {
                handle.close()
            } catch (_: IOException) {
            }
            try {
                fileSystem.delete(tempPath, mustExist = false)
            } catch (_: IOException) {
            }
        }
    }

    /**
     * Random-access reader over a complete `.pcm` file. Holds a single [FileHandle]; safe for
     * sequential or random reads from a single coroutine.
     */
    internal class Reader(
        private val handle: FileHandle,
        val path: Path,
        val header: Header,
    ) {
        @Volatile
        private var closed = false

        val sampleRate: Int get() = header.sampleRate
        val totalFrames: Long get() = header.frameCount

        fun readFrames(buffer: ShortArray, atFramePosition: Long): Int {
            if (closed) return 0
            if (atFramePosition >= header.frameCount) return 0
            val maxFrames = (header.frameCount - atFramePosition).coerceAtMost(
                (buffer.size / 2).toLong()
            ).toInt()
            if (maxFrames <= 0) return 0

            val byteCount = maxFrames * PcmCacheFormat.BYTES_PER_FRAME
            val bytes = ByteArray(byteCount)
            val byteOffset = PcmCacheFormat.HEADER_SIZE_BYTES.toLong() +
                atFramePosition * PcmCacheFormat.BYTES_PER_FRAME
            var totalRead = 0
            while (totalRead < byteCount) {
                val n = handle.read(byteOffset + totalRead, bytes, totalRead, byteCount - totalRead)
                if (n < 0) break
                totalRead += n
            }
            val shortsRead = totalRead / 2
            for (i in 0 until shortsRead) {
                val lo = bytes[i * 2].toInt() and 0xFF
                val hi = bytes[i * 2 + 1].toInt() and 0xFF
                buffer[i] = ((hi shl 8) or lo).toShort()
            }
            return shortsRead / 2
        }

        fun touch() = touchLastModified(path)

        fun close() {
            if (closed) return
            closed = true
            try {
                handle.close()
            } catch (_: IOException) {
            }
        }
    }
}
