package net.sigmabeta.chipbox.js.wasm

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int16Array

/**
 * Low-level wrapper around [ChipboxSsfModule]. Path-based load (psflib reads via `fopen` →
 * Emscripten MEMFS); PCM-out is copied straight into the caller's destination from one reusable
 * WASM-heap staging buffer.
 *
 * The caller (`WasmSsfEmulator`) is responsible for mirroring the staged track directory into
 * MEMFS before `loadTrack(path)` — see [mirrorDirToMemfs].
 *
 * Staging design: one WASM-heap buffer is kept for the lifetime of the core, sized to the largest
 * frame request seen and grown on demand. `_malloc`/`_free` no longer run per generated buffer, so
 * playback performs no allocation on the audio path. Emscripten memory growth keeps the pointer
 * (a byte offset) valid but replaces the heap's `ArrayBuffer`, so the source view is re-derived
 * from [ChipboxSsfModule.HEAP16] on every call and never cached. [teardown] and [loadTrack] free
 * the staging pointer and reset its capacity, so a reload can never reuse freed memory.
 */
class SsfWasmCore(private val module: ChipboxSsfModule) {

    /** Staging pointer in the WASM heap (0 = none) and its capacity in frames. */
    private var pcmPtr: Int = 0
    private var pcmFrames: Int = 0

    fun loadTrack(path: String) {
        // The native loadFile() tears the previous track down, so any staged PCM buffer is dead.
        releasePcmBuffer()

        // C string in the WASM heap. `lengthBytesUTF8` excludes the NUL; +1 for the terminator.
        val byteLen = module.lengthBytesUTF8(path) + 1
        val ptr = module._malloc(byteLen)
        if (ptr == 0) error("WASM heap exhausted: failed to allocate $byteLen bytes for path string")
        try {
            module.stringToUTF8(path, ptr, byteLen)
            val rc = module._chipbox_ssf_load_file(ptr)
            if (rc != 0) error("ssf core: ${lastError()}")
        } finally {
            module._free(ptr)
        }
    }

    /**
     * Render up to [frames] frames into [out] and return the number of frames copied. Copies at
     * most the requested frame count and never writes past [out], preserving the short-render
     * contract of the native core.
     */
    fun play(frames: Int, out: ShortArray): Int {
        if (frames <= 0 || out.isEmpty()) return 0
        ensurePcmCapacity(frames)

        val produced = module._chipbox_ssf_play(pcmPtr, frames)
        val samples = if (produced <= 0) {
            failIfCoreReportedError()
            0
        } else {
            minOf(minOf(produced, frames) * 2, out.size).also { copyInto(out, it) }
        }
        return samples / 2
    }

    fun sampleRate(): Int = module._chipbox_ssf_sample_rate()

    fun teardown() {
        // Free before the core tears down: the next loadTrack must allocate afresh rather than
        // reuse a pointer into freed WASM heap.
        releasePcmBuffer()
        module._chipbox_ssf_teardown()
    }

    private fun ensurePcmCapacity(frames: Int) {
        if (frames <= pcmFrames) return

        val bytes = frames.toLong() * 2 * Short.SIZE_BYTES
        if (bytes > Int.MAX_VALUE) error("PCM staging request too large: $frames frames")

        releasePcmBuffer()
        val ptr = module._malloc(bytes.toInt())
        if (ptr == 0) error("WASM heap exhausted: failed to allocate $bytes bytes for PCM output")
        pcmPtr = ptr
        pcmFrames = frames
    }

    /**
     * One WASM-heap → JS-heap copy. `out` is a Kotlin ShortArray, i.e. an Int16Array at the JS
     * level, so a typed-array `set` is a bulk copy — no temporary array, no element loop. The
     * source view is re-derived from [ChipboxSsfModule.HEAP16] on every call: memory growth keeps
     * the pointer valid but replaces the backing `ArrayBuffer`.
     */
    private fun copyInto(out: ShortArray, samples: Int) {
        val dest = out.unsafeCast<Int16ArrayShim>()
        Int16Array(dest.buffer, dest.byteOffset, samples)
            .set(Int16Array(module.HEAP16.buffer, pcmPtr, samples))
    }

    private fun failIfCoreReportedError() {
        val err = lastError()
        if (err.isNotBlank()) error("ssf core: $err")
    }

    private fun releasePcmBuffer() {
        if (pcmPtr != 0) {
            module._free(pcmPtr)
            pcmPtr = 0
        }
        pcmFrames = 0
    }

    private fun lastError(): String = module.UTF8ToString(module._chipbox_ssf_last_error())
}

/**
 * Lets a Kotlin `ShortArray` be reinterpreted as the `Int16Array` that backs it, so [SsfWasmCore]
 * can bulk-copy into it with a typed-array `set`. Kotlin/JS arrays have no public typed-array
 * accessor; the same `unsafeCast` trick is used by `mirrorDirToMemfs` for `ByteArray`.
 */
private external class Int16ArrayShim {
    val buffer: ArrayBuffer
    val byteOffset: Int
}
