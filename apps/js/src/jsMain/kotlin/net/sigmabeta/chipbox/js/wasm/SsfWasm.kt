@file:Suppress("FunctionName", "VariableNaming", "PropertyName")

package net.sigmabeta.chipbox.js.wasm

import kotlin.js.Promise
import org.khronos.webgl.Uint8Array

/**
 * Kotlin/JS bindings for the Emscripten-built SSF/DSF emulator
 * (`cbox/native/ssf/Ssf_Web.cpp`).
 *
 * The CMake build emits `chipbox_ssf.js` + `chipbox_ssf.wasm` with `MODULARIZE=1` +
 * `EXPORT_NAME=chipboxSsf` + `FORCE_FILESYSTEM=1` so the runtime `FS` helper is available —
 * psflib's chain-file loader hits Emscripten's MEMFS via the existing `chipbox_psf_io.cpp`
 * `fopen` callbacks, so `WasmSsfEmulator` mirrors the staged track dir into MEMFS before
 * calling `_chipbox_ssf_load_file(path)`.
 */
external interface ChipboxSsfModule {
    fun _malloc(size: Int): Int
    fun _free(ptr: Int)

    /** Allocates a NUL-terminated C string in the WASM heap and returns the pointer. */
    fun stringToUTF8(str: String, ptr: Int, maxBytes: Int)
    fun lengthBytesUTF8(str: String): Int

    fun _chipbox_ssf_load_file(pathPtr: Int): Int
    fun _chipbox_ssf_play(targetPtr: Int, frames: Int): Int
    fun _chipbox_ssf_sample_rate(): Int
    fun _chipbox_ssf_last_error(): Int
    fun _chipbox_ssf_teardown()

    fun UTF8ToString(ptr: Int): String

    val HEAPU8: Uint8Array
    val HEAP16: org.khronos.webgl.Int16Array
    val FS: EmscriptenFs
}

private external val chipboxSsf: () -> Promise<ChipboxSsfModule>

suspend fun loadChipboxSsf(): ChipboxSsfModule =
    loadWasmModule("ssf", "/wasm/chipbox_ssf.js") { chipboxSsf() }
