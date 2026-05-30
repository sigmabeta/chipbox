@file:Suppress("FunctionName", "VariableNaming", "PropertyName")

package net.sigmabeta.chipbox.js.wasm

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLScriptElement
import org.w3c.dom.events.Event

/**
 * Shared loader for every Emscripten-built emulator module. Each emulator's `Cmake_Web.cpp` is
 * linked with `MODULARIZE=1 + EXPORT_NAME=<factoryName>`, so the corresponding `.js` exposes a
 * single factory function on `window` once the script tag loads. This helper handles the
 * `<script>` injection, the post-`load` factory invocation, and the per-name module cache so
 * concurrent callers don't double-load.
 *
 * Per-emulator wiring is two declarations + one suspend call (see `GmeWasm.kt` / `VgmWasm.kt`).
 */
suspend fun <T : Any> loadWasmModule(
    name: String,
    loaderUrl: String,
    factory: () -> Promise<T>,
): T {
    @Suppress("UNCHECKED_CAST")
    moduleCache[name]?.let { return it as T }
    val promise = pendingLoads[name]?.unsafeCast<Promise<T>>()
        ?: injectAndInstantiate(name, loaderUrl, factory).also {
            pendingLoads[name] = it.unsafeCast<Promise<Any>>()
        }
    return suspendCancellableCoroutine { cont ->
        promise.then(
            { module ->
                moduleCache[name] = module
                cont.resume(module)
                undefined
            },
            { err ->
                cont.resumeWithException(RuntimeException("Failed to load WASM module '$name': $err"))
                undefined
            },
        )
    }
}

// Kotlin/JS is single-threaded; plain maps suffice.
private val moduleCache: MutableMap<String, Any> = mutableMapOf()
private val pendingLoads: MutableMap<String, Promise<Any>> = mutableMapOf()

private fun <T : Any> injectAndInstantiate(
    name: String,
    loaderUrl: String,
    factory: () -> Promise<T>,
): Promise<T> {
    val selector = "script[data-chipbox-wasm=\"$name\"]"
    val existing = document.querySelector(selector) as? HTMLScriptElement
    return if (existing != null) {
        // Script tag already in the DOM (HMR after a hot reload, etc.); the factory global is
        // available immediately, no need to wait for another load event.
        factory()
    } else {
        Promise { resolve, reject ->
            val script = document.createElement("script") as HTMLScriptElement
            script.src = loaderUrl
            script.async = true
            script.setAttribute("data-chipbox-wasm", name)
            script.addEventListener(
                "load",
                {
                    factory().then(
                        { module ->
                            resolve(module)
                            undefined
                        },
                        { err ->
                            reject(Throwable("$name factory rejected: $err"))
                            undefined
                        },
                    )
                },
            )
            script.addEventListener(
                "error",
                { _: Event -> reject(Throwable("Failed to load $loaderUrl")) },
            )
            document.head?.appendChild(script)
        }
    }
}
