package net.sigmabeta.chipbox.js.wasm

import org.khronos.webgl.Int16Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

/**
 * Low-level Kotlin wrapper around the [ChipboxVgmModule] WASM exports. Mirrors [GmeWasmCore]'s
 * shape — heap-alloc, copy bytes in, call the C entry point, copy PCM out, free.
 */
class VgmWasmCore(private val module: ChipboxVgmModule) {

    /**
     * Hands [bytes] to libvgm via the WASM heap. Throws on libvgm error.
     * Returns the player's output sample rate (always 44100 in our Vgm_Web wrapper).
     */
    fun loadTrack(bytes: ByteArray): Int {
        val size = bytes.size
        val ptr = module._malloc(size)
        if (ptr == 0) error("WASM heap exhausted: failed to allocate $size bytes for track data")
        try {
            val view = Uint8Array(module.HEAPU8.buffer, ptr, size)
            view.set(Uint8Array(bytes.unsafeCast<Int8ArrayShim>().buffer, 0, size))
            val rc = module._chipbox_vgm_load_data(ptr, size)
            if (rc != 0) error("libvgm: ${lastError()}")
        } finally {
            module._free(ptr)
        }
        return module._chipbox_vgm_sample_rate()
    }

    /** See [GmeWasmCore.play] — same shape: returns null at end-of-track, otherwise a
     *  `produced * 2`-sized ShortArray of interleaved L/R 16-bit PCM. */
    fun play(frames: Int): ShortArray? {
        val bytes = frames * 2 * Short.SIZE_BYTES
        val ptr = module._malloc(bytes)
        if (ptr == 0) error("WASM heap exhausted: failed to allocate $bytes bytes for PCM output")
        try {
            val produced = module._chipbox_vgm_play(ptr, frames)
            if (produced == 0) {
                val err = lastError()
                if (err.isNotBlank()) error("libvgm: $err")
                return null
            }
            val out = ShortArray(produced * 2)
            val heap = Int16Array(module.HEAP16.buffer, ptr, produced * 2)
            for (i in 0 until produced * 2) out[i] = heap[i]
            return out
        } finally {
            module._free(ptr)
        }
    }

    fun sampleRate(): Int = module._chipbox_vgm_sample_rate()

    fun teardown() = module._chipbox_vgm_teardown()

    private fun lastError(): String = module.UTF8ToString(module._chipbox_vgm_last_error())
}

// Same `Int8Array` shim as in `GmeWasmCore` — let us reinterpret a Kotlin `ByteArray`'s backing
// buffer as Uint8Array for bulk `Uint8Array.set` copies into the WASM heap.
private external class Int8ArrayShim {
    val buffer: org.khronos.webgl.ArrayBuffer
}
