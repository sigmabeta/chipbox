@file:Suppress("FunctionName", "VariableNaming", "PropertyName", "ktlint:standard:filename")

package net.sigmabeta.chipbox.js.wasm

import kotlin.js.Promise
import org.khronos.webgl.Uint8Array

/**
 * Kotlin/JS bindings for the Emscripten-built slopsf (PSF1/PSF2) emulator
 * (`cbox/native/psf/Psf_Web.cpp`). Both PSX and PS2 tracks route through here — the C side
 * sniffs the PSF version and switches between PSF1 and PSF2 init paths internally.
 *
 * Chain files (`_libN.psf` / `_libN.psf2`, plus PSF2's directory-structured wave-bank refs)
 * are read via psflib's `fopen`-backed callbacks against Emscripten MEMFS, which
 * `WasmPsfEmulator` populates from the okio `FakeFileSystem` staging dir before load.
 */
external interface ChipboxPsfModule {
    fun _malloc(size: Int): Int
    fun _free(ptr: Int)
    fun stringToUTF8(str: String, ptr: Int, maxBytes: Int)
    fun lengthBytesUTF8(str: String): Int

    fun _chipbox_psf_load_file(pathPtr: Int): Int
    fun _chipbox_psf_play(targetPtr: Int, frames: Int): Int
    fun _chipbox_psf_sample_rate(): Int
    fun _chipbox_psf_last_error(): Int
    fun _chipbox_psf_teardown()

    fun UTF8ToString(ptr: Int): String

    val HEAPU8: Uint8Array
    val HEAP16: org.khronos.webgl.Int16Array
    val FS: EmscriptenFs
}

private external val chipboxPsf: () -> Promise<ChipboxPsfModule>

suspend fun loadChipboxPsf(): ChipboxPsfModule =
    loadWasmModule("psf", "/wasm/chipbox_psf.js") { chipboxPsf() }
