@file:Suppress("FunctionName", "VariableNaming", "PropertyName")

package net.sigmabeta.chipbox.js.wasm

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.Int16Array
import org.khronos.webgl.Uint8Array
import org.w3c.dom.HTMLScriptElement
import org.w3c.dom.events.Event

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

    // Runtime helpers exported by `-sEXPORTED_RUNTIME_METHODS=...`.
    fun UTF8ToString(ptr: Int): String

    /** Heap view for copying bytes into and out of the WASM memory. */
    val HEAPU8: Uint8Array
    val HEAP16: Int16Array
}

/** The factory function the Emscripten loader exposes on `window` after the script tag loads. */
private external val chipboxGme: () -> Promise<ChipboxGmeModule>

private const val WASM_LOADER_URL = "/wasm/chipbox_gme.js"

// Kotlin/JS is single-threaded; no @Volatile needed. The module + promise caches are written
// once on first load and read everywhere after.
private var cachedModule: ChipboxGmeModule? = null
private var loadingPromise: Promise<ChipboxGmeModule>? = null

/**
 * Returns the loaded `chipbox_gme` module, injecting the loader script on first call. Idempotent
 * — concurrent callers share the same Promise, and a cached module short-circuits after the first
 * successful load.
 */
suspend fun loadChipboxGme(): ChipboxGmeModule {
    cachedModule?.let { return it }
    val promise = loadingPromise ?: ensureScriptInjected().also { loadingPromise = it }
    return suspendCancellableCoroutine { cont ->
        promise.then(
            { module ->
                cachedModule = module
                cont.resume(module)
                undefined
            },
            { err ->
                cont.resumeWithException(RuntimeException("Failed to load chipbox_gme.wasm: $err"))
                undefined
            },
        )
    }
}

private fun ensureScriptInjected(): Promise<ChipboxGmeModule> {
    val existing = document.querySelector("script[data-chipbox-wasm=\"gme\"]") as? HTMLScriptElement
    return if (existing != null) {
        // Script already in the DOM — assume it loaded; call the factory directly.
        chipboxGme()
    } else {
        Promise { resolve, reject ->
            val script = document.createElement("script") as HTMLScriptElement
            script.src = WASM_LOADER_URL
            script.async = true
            script.setAttribute("data-chipbox-wasm", "gme")
            script.addEventListener(
                "load",
                {
                    chipboxGme().then(
                        { module -> resolve(module); undefined },
                        { err -> reject(Throwable("chipboxGme() rejected: $err")); undefined },
                    )
                },
            )
            script.addEventListener(
                "error",
                { _: Event -> reject(Throwable("Failed to load $WASM_LOADER_URL")) },
            )
            document.head?.appendChild(script)
        }
    }
}
