package net.sigmabeta.chipbox.js.wasm

import org.khronos.webgl.Int16Array
import org.khronos.webgl.get

/**
 * Low-level wrapper around [ChipboxPsfModule]. Identical shape to [SsfWasmCore] /
 * [UsfWasmCore] — path-based load (psflib reads through `fopen` → MEMFS), heap-alloc /
 * render / copy / free for PCM output.
 */
class PsfWasmCore(private val module: ChipboxPsfModule) {

    fun loadTrack(path: String) {
        val byteLen = module.lengthBytesUTF8(path) + 1
        val ptr = module._malloc(byteLen)
        if (ptr == 0) error("WASM heap exhausted: failed to allocate $byteLen bytes for path string")
        try {
            module.stringToUTF8(path, ptr, byteLen)
            val rc = module._chipbox_psf_load_file(ptr)
            if (rc != 0) error("slopsf: ${lastError()}")
        } finally {
            module._free(ptr)
        }
    }

    fun play(frames: Int): ShortArray? {
        val bytes = frames * 2 * Short.SIZE_BYTES
        val ptr = module._malloc(bytes)
        if (ptr == 0) error("WASM heap exhausted: failed to allocate $bytes bytes for PCM output")
        try {
            val produced = module._chipbox_psf_play(ptr, frames)
            if (produced == 0) {
                val err = lastError()
                if (err.isNotBlank()) error("slopsf: $err")
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

    fun sampleRate(): Int = module._chipbox_psf_sample_rate()

    fun teardown() = module._chipbox_psf_teardown()

    private fun lastError(): String = module.UTF8ToString(module._chipbox_psf_last_error())
}
