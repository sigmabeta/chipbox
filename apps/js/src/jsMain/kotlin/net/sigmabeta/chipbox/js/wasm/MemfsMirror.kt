@file:Suppress("FunctionName", "VariableNaming")

package net.sigmabeta.chipbox.js.wasm

import okio.FileSystem
import okio.Path
import org.khronos.webgl.Uint8Array

/**
 * Bridge from `okio.FakeFileSystem` (where `RealPcmTrackSourceFactory` + `TrackStager` write each
 * track's staged bytes) into an Emscripten module's MEMFS (where C-side `fopen` reads). Required
 * for the PSF-family emulators (psf/ssf/usf) — psflib's chain-file loader calls `fopen("_lib.psf")`
 * etc., expecting siblings of the main file to exist on disk.
 *
 * The `FS` runtime helper has to be exported from the Emscripten module — add `'FS'` to
 * `-sEXPORTED_RUNTIME_METHODS` in the per-emulator CMakeLists EMSCRIPTEN branch.
 */
external interface EmscriptenFs {
    fun mkdirTree(path: String)
    fun writeFile(path: String, data: Uint8Array)
    fun analyzePath(path: String): EmscriptenFsAnalysis
    fun unlink(path: String)
    fun rmdir(path: String)
    fun readdir(path: String): Array<String>
}

external interface EmscriptenFsAnalysis {
    val exists: Boolean
}

/**
 * Copies every file under [sourceDir] (in [sourceFs], typically the project-wide FakeFileSystem)
 * into [targetFs] (an Emscripten module's MEMFS) at the same absolute path. Idempotent — uses
 * `mkdirTree` so re-staging the same path overwrites without erroring.
 *
 * Does NOT recurse into subdirectories. `TrackStager` lays out each track as
 * `stagingDir/track-N/main.<ext>` plus flat `_lib*.<ext>` siblings, no nesting.
 */
fun mirrorDirToMemfs(
    sourceFs: FileSystem,
    sourceDir: Path,
    targetFs: EmscriptenFs,
) {
    targetFs.mkdirTree(sourceDir.toString())
    val entries = sourceFs.listOrNull(sourceDir) ?: return
    for (entry in entries) {
        val md = sourceFs.metadataOrNull(entry) ?: continue
        if (md.isDirectory) continue   // TrackStager doesn't nest; refuse rather than recurse blindly.
        val bytes = sourceFs.read(entry) { readByteArray() }
        val view = byteArrayToUint8Array(bytes)
        targetFs.writeFile(entry.toString(), view)
    }
}

/**
 * Reinterprets a Kotlin [ByteArray] as a [Uint8Array] view of the same memory for a bulk copy
 * — much faster than per-element assignment for the multi-MB chain files PSF tracks can carry.
 */
private fun byteArrayToUint8Array(bytes: ByteArray): Uint8Array {
    // Kotlin/JS backs ByteArray with a typed array; the dynamic cast exposes its `.buffer`.
    val anyArr = bytes.unsafeCast<Int8ArrayShim>()
    return Uint8Array(anyArr.buffer, 0, bytes.size)
}

/**
 * Lets us reinterpret a Kotlin/JS `ByteArray` as its underlying typed-array so we can pull out
 * `.buffer` for bulk `Uint8Array.set` copies into the WASM heap. Shared by [byteArrayToUint8Array]
 * here and `VgmWasmCore` — keep it in one place since `external class` declarations are
 * name-visible at the JS level and would otherwise collide.
 */
internal external class Int8ArrayShim {
    val buffer: org.khronos.webgl.ArrayBuffer
}
