package net.sigmabeta.chipbox.js.wasm

import org.khronos.webgl.Int16Array
import org.khronos.webgl.get

class VgmstreamWasmCore(private val module: ChipboxVgmstreamModule) {

    fun loadTrack(path: String, subsong: Int) {
        val byteLen = module.lengthBytesUTF8(path) + 1
        val ptr = module._malloc(byteLen)
        if (ptr == 0) error("WASM heap exhausted: failed to allocate $byteLen bytes for path string")
        try {
            module.stringToUTF8(path, ptr, byteLen)
            val rc = module._chipbox_vgmstream_load_file(ptr, subsong)
            if (rc != 0) error("vgmstream: ${lastError()}")
        } finally {
            module._free(ptr)
        }
    }

    fun play(frames: Int): ShortArray? {
        val bytes = frames * 2 * Short.SIZE_BYTES
        val ptr = module._malloc(bytes)
        if (ptr == 0) error("WASM heap exhausted: failed to allocate $bytes bytes for PCM output")
        try {
            val produced = module._chipbox_vgmstream_play(ptr, frames)
            if (produced == 0) {
                val err = lastError()
                if (err.isNotBlank()) error("vgmstream: $err")
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

    fun sampleRate(): Int = module._chipbox_vgmstream_sample_rate()

    fun teardown() = module._chipbox_vgmstream_teardown()

    /** Split the comma-separated string the C side caches into a list. */
    fun supportedExtensions(): List<String> {
        val ptr = module._chipbox_vgmstream_supported_extensions()
        if (ptr == 0) return emptyList()
        val csv = module.UTF8ToString(ptr)
        return csv.split(',').filter { it.isNotBlank() }
    }

    private fun lastError(): String = module.UTF8ToString(module._chipbox_vgmstream_last_error())
}
