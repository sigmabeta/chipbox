@file:Suppress("FunctionName", "VariableNaming", "PropertyName")

package net.sigmabeta.chipbox.js.wasm

import kotlin.js.Promise
import org.khronos.webgl.Uint8Array

/**
 * Kotlin/JS bindings for the Emscripten-built USF emulator (`cbox/native/usf/Usf_Web.cpp`).
 *
 * USF is the Nintendo 64 PSF dialect. Same flow as SSF — psflib's chain-file loader reads
 * `_lib.usf` siblings via `fopen` (handled by Emscripten MEMFS). The CMake build uses the
 * cached-interpreter R4300 core (no x86 dynarec on the web). Module name `chipboxUsf` mirrors
 * the `-sEXPORT_NAME` flag in the CMakeLists EMSCRIPTEN branch.
 */
external interface ChipboxUsfModule {
    fun _malloc(size: Int): Int
    fun _free(ptr: Int)
    fun stringToUTF8(str: String, ptr: Int, maxBytes: Int)
    fun lengthBytesUTF8(str: String): Int

    fun _chipbox_usf_load_file(pathPtr: Int): Int
    fun _chipbox_usf_play(targetPtr: Int, frames: Int): Int
    fun _chipbox_usf_sample_rate(): Int
    fun _chipbox_usf_last_error(): Int
    fun _chipbox_usf_teardown()

    fun UTF8ToString(ptr: Int): String

    val HEAPU8: Uint8Array
    val HEAP16: org.khronos.webgl.Int16Array
    val FS: EmscriptenFs
}

private external val chipboxUsf: () -> Promise<ChipboxUsfModule>

suspend fun loadChipboxUsf(): ChipboxUsfModule =
    loadWasmModule("usf", "/wasm/chipbox_usf.js") { chipboxUsf() }
