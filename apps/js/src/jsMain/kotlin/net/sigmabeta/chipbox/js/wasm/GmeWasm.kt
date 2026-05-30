@file:Suppress("FunctionName", "VariableNaming", "PropertyName", "ktlint:standard:filename")

package net.sigmabeta.chipbox.js.wasm

import kotlin.js.Promise
import org.khronos.webgl.Int16Array
import org.khronos.webgl.Uint8Array

/**
 * Kotlin/JS bindings for the Emscripten-built libgme module (`cbox/native/gme/Gme_Web.cpp`).
 *
 * The CMake build emits `chipbox_gme.js` + `chipbox_gme.wasm` with `MODULARIZE=1` +
 * `EXPORT_NAME=chipboxGme`, so the `.js` file exposes a single factory function on `window`
 * (`chipboxGme()` → `Promise<ChipboxGmeModule>`). The `.wasm` is auto-located relative to the
 * loader script's URL — both files live under `/wasm/` in the served bundle.
 *
 * The C wrapper uses static globals (single emulator instance at a time); the Kotlin layer
 * mirrors that — one `ChipboxGmeModule` per page, serialize calls to it via a single coroutine.
 * Multi-track concurrency (e.g. preview + main player) is out of scope; same constraint the
 * desktop JNI build has.
 */
external interface ChipboxGmeModule {
    fun _malloc(size: Int): Int
    fun _free(ptr: Int)

    // Direct exports from Gme_Web.cpp (EMSCRIPTEN_KEEPALIVE → `_<name>` symbol).
    fun _chipbox_gme_load_data(dataPtr: Int, size: Int, track: Int): Int
    fun _chipbox_gme_play(targetPtr: Int, frames: Int): Int
    fun _chipbox_gme_sample_rate(): Int
    fun _chipbox_gme_seek_ms(positionMs: Int): Int
    fun _chipbox_gme_tell_ms(): Int

    /** Pointer into the WASM heap (static lifetime); read via [UTF8ToString]. */
    fun _chipbox_gme_last_error(): Int
    fun _chipbox_gme_teardown()

    fun UTF8ToString(ptr: Int): String

    val HEAPU8: Uint8Array
    val HEAP16: Int16Array
}

private external val chipboxGme: () -> Promise<ChipboxGmeModule>

suspend fun loadChipboxGme(): ChipboxGmeModule =
    loadWasmModule("gme", "/wasm/chipbox_gme.js") { chipboxGme() }
