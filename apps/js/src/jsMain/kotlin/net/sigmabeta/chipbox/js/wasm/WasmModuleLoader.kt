@file:Suppress("FunctionName", "VariableNaming", "PropertyName")

package net.sigmabeta.chipbox.js.wasm

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlin.js.jsTypeOf
import kotlinx.browser.document
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import net.sigmabeta.chipbox.player.emulators.WasmLoadFailure
import net.sigmabeta.chipbox.player.emulators.WasmLoadFailureKind
import org.w3c.dom.HTMLScriptElement
import org.w3c.dom.events.Event

/**
 * Shared loader for every Emscripten-built emulator module. Each emulator's `Cmake_Web.cpp` is
 * linked with `MODULARIZE=1 + EXPORT_NAME=<factoryName>`, so the corresponding `.js` exposes a
 * single factory function on `window` once the script tag loads. This helper handles the
 * `<script>` injection, the post-`load` factory invocation, and the per-name module cache so
 * concurrent callers don't double-load.
 *
 * Failure handling matters as much as loading here: the server used to answer a missing
 * `/wasm/…js` with the SPA's `index.html` and HTTP 200, so the injected script SyntaxError'd, the
 * `load` event still fired, and calling the (undefined) factory threw *outside* the promise
 * executor — leaving the playback coroutine awaiting forever. Every path now settles:
 *
 * - the script must fire `load` (or `error`) before [LOAD_TIMEOUT_MS];
 * - the global named by [factoryName] must be a function after `load`;
 * - the factory call is wrapped, so a synchronous throw rejects instead of escaping;
 * - the whole settle (including the module's own `.wasm` fetch) is bounded by [LOAD_TIMEOUT_MS];
 * - failed entries are evicted from [pendingLoads] and the `<script>` is removed, so a retry is
 *   possible without reloading the page;
 * - the thrown message names the URL and the likely packaging cause (see [WasmLoadFailure]).
 *
 * Per-emulator wiring is three declarations + one suspend call (see `GmeWasm.kt` / `VgmWasm.kt`).
 */
suspend fun <T : Any> loadWasmModule(
    name: String,
    loaderUrl: String,
    factoryName: String,
    factory: () -> Promise<T>,
): T {
    @Suppress("UNCHECKED_CAST")
    moduleCache[name]?.let { return it as T }

    val pending = pendingLoads[name]?.unsafeCast<Promise<T>>()
        ?: injectAndInstantiate(name, loaderUrl, factoryName, factory).also {
            pendingLoads[name] = it.unsafeCast<Promise<Any>>()
        }

    val module = try {
        withTimeout(LOAD_TIMEOUT_MS) { pending.awaitModule(name, loaderUrl) }
    } catch (t: Throwable) {
        // Caller cancellation (e.g. the user skipped the track) is not a load failure: leave the
        // shared in-flight load and its script alone for the next caller, and keep structured
        // cancellation intact. Only the loader's own timeout is a failure.
        if (t is CancellationException && t !is TimeoutCancellationException) throw t
        // A failed attempt must not be cached: that was the "retry needs a page reload" bug.
        pendingLoads.remove(name)
        removeScript(name)
        throw RuntimeException(failureFor(name, loaderUrl, t).message, t)
    }

    moduleCache[name] = module
    pendingLoads.remove(name)
    removeScript(name)
    return module
}

// Kotlin/JS is single-threaded; plain maps suffice.
private val moduleCache: MutableMap<String, Any> = mutableMapOf()
private val pendingLoads: MutableMap<String, Promise<Any>> = mutableMapOf()

private fun failureFor(name: String, loaderUrl: String, cause: Throwable): WasmLoadFailure = when (cause) {
    is WasmModuleLoadException -> cause.failure

    is TimeoutCancellationException -> WasmLoadFailure(
        moduleName = name,
        loaderUrl = loaderUrl,
        kind = WasmLoadFailureKind.TIMEOUT,
        detail = "no successful response within ${LOAD_TIMEOUT_MS / MILLIS_PER_SECOND} seconds",
    )

    else -> WasmLoadFailure(
        moduleName = name,
        loaderUrl = loaderUrl,
        kind = WasmLoadFailureKind.MODULE_REJECTED,
        detail = cause.message ?: cause.toString(),
    )
}

private fun <T : Any> injectAndInstantiate(
    name: String,
    loaderUrl: String,
    factoryName: String,
    factory: () -> Promise<T>,
): Promise<T> {
    val selector = scriptSelector(name)
    val existing = document.querySelector(selector) as? HTMLScriptElement
    return if (existing != null) {
        // Script tag already in the DOM (HMR after a hot reload, etc.); the factory global is
        // available immediately, no need to wait for another load event.
        callFactory(name, loaderUrl, factoryName, factory, script = existing)
    } else {
        Promise { resolve, reject ->
            val script = document.createElement("script") as HTMLScriptElement
            script.src = loaderUrl
            script.async = true
            script.setAttribute("data-chipbox-wasm", name)
            script.addEventListener(
                "load",
                {
                    val globalFactory = js("window[factoryName]")
                    val failureKind = WasmLoadFailure.failureKind(
                        factoryIsFunction = jsTypeOf(globalFactory) == "function",
                        scriptError = false,
                    )
                    if (failureKind != null) {
                        script.remove()
                        reject(
                            WasmModuleLoadException(
                                WasmLoadFailure(
                                    moduleName = name,
                                    loaderUrl = loaderUrl,
                                    kind = failureKind,
                                    detail = "the global '$factoryName' is ${jsTypeOf(globalFactory)}",
                                )
                            )
                        )
                        return@addEventListener
                    }
                    try {
                        factory().then(
                            { module ->
                                script.remove()
                                resolve(module)
                                undefined
                            },
                            { err ->
                                script.remove()
                                reject(
                                    WasmModuleLoadException(
                                        WasmLoadFailure(
                                            moduleName = name,
                                            loaderUrl = loaderUrl,
                                            kind = WasmLoadFailureKind.FACTORY_REJECTED,
                                            detail = "$err",
                                        )
                                    )
                                )
                                undefined
                            },
                        )
                    } catch (t: Throwable) {
                        // Defensive: `jsTypeOf` said the global is callable, but the actual
                        // invocation can still throw (e.g. a partially-initialized global).
                        script.remove()
                        reject(
                            WasmModuleLoadException(
                                WasmLoadFailure(
                                    moduleName = name,
                                    loaderUrl = loaderUrl,
                                    kind = WasmLoadFailureKind.FACTORY_REJECTED,
                                    detail = t.message ?: t.toString(),
                                )
                            )
                        )
                    }
                },
            )
            script.addEventListener(
                "error",
                { _: Event ->
                    script.remove()
                    reject(
                        WasmModuleLoadException(
                            WasmLoadFailure(
                                moduleName = name,
                                loaderUrl = loaderUrl,
                                kind = WasmLoadFailureKind.SCRIPT_ERROR,
                                detail = "the script element fired an error event",
                            )
                        )
                    )
                },
            )
            document.head?.appendChild(script)
        }
    }
}

private fun <T : Any> callFactory(
    name: String,
    loaderUrl: String,
    factoryName: String,
    factory: () -> Promise<T>,
    script: HTMLScriptElement,
): Promise<T> {
    val globalFactory = js("window[factoryName]")
    val failureKind = WasmLoadFailure.failureKind(
        factoryIsFunction = jsTypeOf(globalFactory) == "function",
        scriptError = false,
    )
    if (failureKind != null) {
        script.remove()
        return Promise { _, reject ->
            reject(
                WasmModuleLoadException(
                    WasmLoadFailure(
                        moduleName = name,
                        loaderUrl = loaderUrl,
                        kind = failureKind,
                        detail = "the global '$factoryName' is ${jsTypeOf(globalFactory)}",
                    )
                )
            )
        }
    }
    return try {
        factory()
    } catch (t: Throwable) {
        script.remove()
        Promise { _, reject ->
            reject(
                WasmModuleLoadException(
                    WasmLoadFailure(
                        moduleName = name,
                        loaderUrl = loaderUrl,
                        kind = WasmLoadFailureKind.FACTORY_REJECTED,
                        detail = t.message ?: t.toString(),
                    )
                )
            )
        }
    }
}

private fun scriptSelector(name: String): String = "script[data-chipbox-wasm=\"$name\"]"

private fun removeScript(name: String) {
    (document.querySelector(scriptSelector(name)) as? HTMLScriptElement)?.remove()
}

private suspend fun <T> Promise<T>.awaitModule(name: String, loaderUrl: String): T =
    suspendCancellableCoroutine { cont ->
        then(
            { module ->
                cont.resume(module)
                undefined
            },
            { err ->
                cont.resumeWithException(
                    err as? WasmModuleLoadException
                        ?: WasmModuleLoadException(
                            WasmLoadFailure(
                                moduleName = name,
                                loaderUrl = loaderUrl,
                                kind = WasmLoadFailureKind.MODULE_REJECTED,
                                detail = "$err",
                            )
                        ),
                )
                undefined
            },
        )
    }

/** Carries the classified failure through the Promise machinery so the message is preserved. */
private class WasmModuleLoadException(val failure: WasmLoadFailure) : RuntimeException(failure.message)

private const val MILLIS_PER_SECOND = 1_000L

/** Upper bound for the script fetch *and* the Emscripten module (`.wasm`) initialization. */
private const val LOAD_TIMEOUT_MS = 10_000L
