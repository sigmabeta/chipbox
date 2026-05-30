@file:Suppress("FunctionName", "VariableNaming", "PropertyName", "ktlint:standard:filename")

package net.sigmabeta.chipbox.js.wasm

import kotlin.js.Promise
import org.khronos.webgl.Uint8Array

/**
 * Kotlin/JS bindings for the Emscripten-built NCSF (NDS Nitro Composer) emulator
 * (`cbox/native/ncsf/Ncsf_Web.cpp`). Same PSF-family flow as SSF/USF/PSF — psflib's
 * chain-file loader reads sibling `_libN.ncsf` files via `fopen` against Emscripten MEMFS,
 * which `WasmNcsfEmulator` populates before each track load.
 */
external interface ChipboxNcsfModule {
    fun _malloc(size: Int): Int
    fun _free(ptr: Int)
    fun stringToUTF8(str: String, ptr: Int, maxBytes: Int)
    fun lengthBytesUTF8(str: String): Int

    fun _chipbox_ncsf_load_file(pathPtr: Int): Int
    fun _chipbox_ncsf_play(targetPtr: Int, frames: Int): Int
    fun _chipbox_ncsf_sample_rate(): Int
    fun _chipbox_ncsf_last_error(): Int
    fun _chipbox_ncsf_teardown()

    fun UTF8ToString(ptr: Int): String

    val HEAPU8: Uint8Array
    val HEAP16: org.khronos.webgl.Int16Array
    val FS: EmscriptenFs
}

private external val chipboxNcsf: () -> Promise<ChipboxNcsfModule>

suspend fun loadChipboxNcsf(): ChipboxNcsfModule =
    loadWasmModule("ncsf", "/wasm/chipbox_ncsf.js") { chipboxNcsf() }
