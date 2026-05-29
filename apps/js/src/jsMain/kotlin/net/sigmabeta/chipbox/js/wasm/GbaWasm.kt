@file:Suppress("FunctionName", "VariableNaming", "PropertyName")

package net.sigmabeta.chipbox.js.wasm

import kotlin.js.Promise
import org.khronos.webgl.Uint8Array

/**
 * Kotlin/JS bindings for the Emscripten-built GSF emulator (`cbox/native/gba/Gba_Web.cpp`).
 * Backed by mgba — the full Game Boy Advance core. PSF-family flow with MEMFS-mirrored
 * chain files.
 */
external interface ChipboxGbaModule {
    fun _malloc(size: Int): Int
    fun _free(ptr: Int)
    fun stringToUTF8(str: String, ptr: Int, maxBytes: Int)
    fun lengthBytesUTF8(str: String): Int

    fun _chipbox_gba_load_file(pathPtr: Int): Int
    fun _chipbox_gba_play(targetPtr: Int, frames: Int): Int
    fun _chipbox_gba_sample_rate(): Int
    fun _chipbox_gba_last_error(): Int
    fun _chipbox_gba_teardown()

    fun UTF8ToString(ptr: Int): String

    val HEAPU8: Uint8Array
    val HEAP16: org.khronos.webgl.Int16Array
    val FS: EmscriptenFs
}

private external val chipboxGba: () -> Promise<ChipboxGbaModule>

suspend fun loadChipboxGba(): ChipboxGbaModule =
    loadWasmModule("gba", "/wasm/chipbox_gba.js") { chipboxGba() }
