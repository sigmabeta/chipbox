package net.sigmabeta.chipbox.js.wasm

import org.khronos.webgl.Int16Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

/**
 * Low-level Kotlin wrapper around the [ChipboxGmeModule] WASM exports. Not an
 * [net.sigmabeta.chipbox.player.emulators.Emulator] subclass itself — that's
 * `WasmGmeEmulator` in the emulators package, which owns the Emulator-interface contract and
 * delegates to this. Splitting the WASM marshalling from the chipbox Emulator contract keeps
 * each file focused: this one knows about Emscripten heaps, the other knows about Track paths.
 *
 * The underlying C wrapper holds a single libgme instance in a static global. Two
 * `GmeWasmCore` instances would step on each other — the audio chain uses one at a time anyway
 * (one Emulator per active track), so this matches the chip-player-js / desktop pattern.
 */
class GmeWasmCore(private val module: ChipboxGmeModule) {

    /**
     * Hands [bytes] to libgme via the WASM heap and starts [track] (0-based). Throws on any
     * libgme error — caller handles. Returns the emulator's chosen output sample rate (44100
     * for everything except SPC, which uses its native 32000).
     */
    fun loadTrack(bytes: ByteArray, track: Int = 0): Int {
        val size = bytes.size
        val ptr = module._malloc(size)
        if (ptr == 0) error("WASM heap exhausted: failed to allocate $size bytes for track data")
        try {
            // Copy from JS ByteArray into the WASM heap. `set(typed, offset)` does the bulk
            // memcpy under the hood; per-byte loops here would be ~50x slower for big PSFs.
            val view = Uint8Array(module.HEAPU8.buffer, ptr, size)
            // Reinterpret the Kotlin ByteArray (Int8Array under the hood) as Uint8Array for the
            // bulk copy — values 128-255 round-trip correctly since we're just moving bytes.
            view.set(Uint8Array(bytes.unsafeCast<Int8Array>().buffer, 0, size))

            val rc = module._chipbox_gme_load_data(ptr, size, track)
            if (rc != 0) error("libgme: ${lastError()}")
        } finally {
            module._free(ptr)
        }
        return module._chipbox_gme_sample_rate()
    }

    /**
     * Renders [frames] stereo frames into a freshly-allocated `ShortArray` (length `frames * 2`,
     * interleaved L/R, signed 16-bit native byte order). Returns null at end-of-track.
     */
    fun play(frames: Int): ShortArray? {
        val bytes = frames * 2 * Short.SIZE_BYTES
        val ptr = module._malloc(bytes)
        if (ptr == 0) error("WASM heap exhausted: failed to allocate $bytes bytes for PCM output")
        try {
            val produced = module._chipbox_gme_play(ptr, frames)
            if (produced == 0) {
                val err = lastError()
                if (err.isNotBlank()) error("libgme: $err")
                return null
            }
            // Snapshot the heap PCM into a Kotlin-owned ShortArray; the heap buffer gets freed in
            // `finally`. Two interleaved samples per frame → `frames * 2`.
            val out = ShortArray(produced * 2)
            val heap = Int16Array(module.HEAP16.buffer, ptr, produced * 2)
            for (i in 0 until produced * 2) out[i] = heap[i]
            return out
        } finally {
            module._free(ptr)
        }
    }

    fun seekMs(positionMs: Int): Boolean {
        val rc = module._chipbox_gme_seek_ms(positionMs)
        return rc == 0
    }

    fun tellMs(): Int = module._chipbox_gme_tell_ms()

    fun sampleRate(): Int = module._chipbox_gme_sample_rate()

    fun teardown() = module._chipbox_gme_teardown()

    private fun lastError(): String = module.UTF8ToString(module._chipbox_gme_last_error())
}

// Kotlin/JS doesn't surface Int8Array under that name in commonMain; declare the bare external
// here so the loadTrack copy can reinterpret a Kotlin ByteArray's backing buffer.
private external class Int8Array {
    val buffer: org.khronos.webgl.ArrayBuffer
}
