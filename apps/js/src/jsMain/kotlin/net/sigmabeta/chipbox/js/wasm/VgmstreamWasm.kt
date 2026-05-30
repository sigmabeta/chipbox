@file:Suppress("FunctionName", "VariableNaming", "PropertyName", "ktlint:standard:filename")

package net.sigmabeta.chipbox.js.wasm

import kotlin.js.Promise
import org.khronos.webgl.Uint8Array

/**
 * Kotlin/JS bindings for the Emscripten-built vgmstream
 * (`cbox/native/vgmstream/Vgmstream_Web.cpp`). Path-based load like the PSF-family emulators,
 * plus a subsong index (vgmstream containers like FSB/AWB pack many streams per file — the
 * subsong rides in as the chipbox track number, matching the JVM `VgmstreamEmulator`).
 *
 * vgmstream's runtime supported-extension list is exposed via
 * [_chipbox_vgmstream_supported_extensions] as a single comma-separated string — the JS
 * binding splits it once at first access rather than hand-maintaining a list that would drift
 * from the native library.
 */
external interface ChipboxVgmstreamModule {
    fun _malloc(size: Int): Int
    fun _free(ptr: Int)
    fun stringToUTF8(str: String, ptr: Int, maxBytes: Int)
    fun lengthBytesUTF8(str: String): Int

    fun _chipbox_vgmstream_load_file(pathPtr: Int, subsong: Int): Int
    fun _chipbox_vgmstream_play(targetPtr: Int, frames: Int): Int
    fun _chipbox_vgmstream_sample_rate(): Int
    fun _chipbox_vgmstream_last_error(): Int
    fun _chipbox_vgmstream_teardown()
    fun _chipbox_vgmstream_supported_extensions(): Int

    fun UTF8ToString(ptr: Int): String

    val HEAPU8: Uint8Array
    val HEAP16: org.khronos.webgl.Int16Array
    val FS: EmscriptenFs
}

private external val chipboxVgmstream: () -> Promise<ChipboxVgmstreamModule>

suspend fun loadChipboxVgmstream(): ChipboxVgmstreamModule =
    loadWasmModule("vgmstream", "/wasm/chipbox_vgmstream.js") { chipboxVgmstream() }
