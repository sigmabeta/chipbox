package net.sigmabeta.chipbox.js.wasm

import org.khronos.webgl.Int16Array
import org.khronos.webgl.get

/**
 * Low-level wrapper around [ChipboxSsfModule]. Path-based load (psflib reads via `fopen` →
 * Emscripten MEMFS); PCM-out matches the GME/VGM shape — alloc heap, render, copy out, free.
 *
 * The caller (`WasmSsfEmulator`) is responsible for mirroring the staged track directory into
 * MEMFS before `loadTrack(path)` — see [mirrorDirToMemfs].
 */
class SsfWasmCore(private val module: ChipboxSsfModule) {

    fun loadTrack(path: String) {
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

    fun play(frames: Int): ShortArray? {
        val bytes = frames * 2 * Short.SIZE_BYTES
        val ptr = module._malloc(bytes)
        if (ptr == 0) error("WASM heap exhausted: failed to allocate $bytes bytes for PCM output")
        try {
            val produced = module._chipbox_ssf_play(ptr, frames)
            if (produced == 0) {
                val err = lastError()
                if (err.isNotBlank()) error("ssf core: $err")
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

    fun sampleRate(): Int = module._chipbox_ssf_sample_rate()

    fun teardown() = module._chipbox_ssf_teardown()

    private fun lastError(): String = module.UTF8ToString(module._chipbox_ssf_last_error())
}
