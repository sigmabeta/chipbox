@file:Suppress("FunctionName", "VariableNaming", "PropertyName")

package net.sigmabeta.chipbox.js.wasm

import kotlin.js.Promise
import org.khronos.webgl.Int16Array
import org.khronos.webgl.Uint8Array

/**
 * Kotlin/JS bindings for the Emscripten-built libvgm module (`cbox/native/vgm/Vgm_Web.cpp`).
 *
 * The CMake build emits `chipbox_vgm.js` + `chipbox_vgm.wasm` with `MODULARIZE=1` +
 * `EXPORT_NAME=chipboxVgm`. libvgm covers VGM + VGZ (gzip-wrapped VGM) — the zlib that
 * `MemoryLoader_Init` uses for VGZ decompression comes in via `-sUSE_ZLIB=1` (Emscripten
 * port; pre-built with `embuilder build zlib`).
 *
 * Like the GME side, libvgm's `PlayerA` is single-track-at-a-time; the Kotlin layer mirrors that.
 */
external interface ChipboxVgmModule {
    fun _malloc(size: Int): Int
    fun _free(ptr: Int)

    fun _chipbox_vgm_load_data(dataPtr: Int, size: Int): Int
    fun _chipbox_vgm_play(targetPtr: Int, frames: Int): Int
    fun _chipbox_vgm_sample_rate(): Int

    /** Pointer into the WASM heap (static lifetime); read via [UTF8ToString]. */
    fun _chipbox_vgm_last_error(): Int
    fun _chipbox_vgm_teardown()

    fun UTF8ToString(ptr: Int): String

    val HEAPU8: Uint8Array
    val HEAP16: Int16Array
}

private external val chipboxVgm: () -> Promise<ChipboxVgmModule>

suspend fun loadChipboxVgm(): ChipboxVgmModule =
    loadWasmModule("vgm", "/wasm/chipbox_vgm.js") { chipboxVgm() }
