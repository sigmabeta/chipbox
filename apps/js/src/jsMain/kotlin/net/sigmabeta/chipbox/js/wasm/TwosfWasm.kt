@file:Suppress("FunctionName", "VariableNaming", "PropertyName", "ktlint:standard:filename")

package net.sigmabeta.chipbox.js.wasm

import kotlin.js.Promise
import org.khronos.webgl.Uint8Array

/**
 * Kotlin/JS bindings for the Emscripten-built 2SF (NDS Sound Format) emulator
 * (`cbox/native/2sf/Twosf_Web.cpp`). Backed by vio2sf, a DeSmuME-derived NDS core. Same
 * PSF-family flow as the other psflib emulators.
 *
 * Kotlin's `2sf` directory name can't be a package segment (must start with a letter), so this
 * binding's module name is `chipboxTwosf` to match the CMake `-sEXPORT_NAME`.
 */
external interface ChipboxTwosfModule {
    fun _malloc(size: Int): Int
    fun _free(ptr: Int)
    fun stringToUTF8(str: String, ptr: Int, maxBytes: Int)
    fun lengthBytesUTF8(str: String): Int

    fun _chipbox_twosf_load_file(pathPtr: Int): Int
    fun _chipbox_twosf_play(targetPtr: Int, frames: Int): Int
    fun _chipbox_twosf_sample_rate(): Int
    fun _chipbox_twosf_last_error(): Int
    fun _chipbox_twosf_teardown()

    fun UTF8ToString(ptr: Int): String

    val HEAPU8: Uint8Array
    val HEAP16: org.khronos.webgl.Int16Array
    val FS: EmscriptenFs
}

private external val chipboxTwosf: () -> Promise<ChipboxTwosfModule>

suspend fun loadChipboxTwosf(): ChipboxTwosfModule =
    loadWasmModule("twosf", "/wasm/chipbox_twosf.js") { chipboxTwosf() }
