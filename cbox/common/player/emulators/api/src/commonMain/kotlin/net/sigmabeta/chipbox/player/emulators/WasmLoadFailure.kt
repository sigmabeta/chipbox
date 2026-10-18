package net.sigmabeta.chipbox.player.emulators

/**
 * Why a browser emulator module (Emscripten `.js` loader + `.wasm` blob) failed to load.
 *
 * The loader itself lives in the web app, but classification is pure so it can be tested under
 * Node and on the JVM alongside the [Emulator] contract. Every case produces one actionable
 * message: the server now returns a real 404 for a missing `/wasm/` file, so the common cause is
 * a bundle built without the WASM toolchain, and saying so beats surfacing a bare DOM event or an
 * uncaught `TypeError` from the injected script's factory global.
 */
data class WasmLoadFailure(
    val moduleName: String,
    val loaderUrl: String,
    val kind: WasmLoadFailureKind,
    val detail: String? = null,
) {
    /** User-facing explanation, safe to route to the snackbar / error log. */
    val message: String
        get() = buildString {
            append("Couldn't load the '$moduleName' emulator module from $loaderUrl: ")
            append(
                when (kind) {
                    WasmLoadFailureKind.TIMEOUT ->
                        "timed out waiting for the script and module initialization"

                    WasmLoadFailureKind.SCRIPT_ERROR ->
                        "the script failed to load (check that /wasm/ is served)"

                    WasmLoadFailureKind.FACTORY_MISSING ->
                        "the script loaded but its factory function is missing — " +
                            "the server may have served HTML for this path"

                    WasmLoadFailureKind.FACTORY_REJECTED ->
                        "the factory rejected while creating the module"

                    WasmLoadFailureKind.MODULE_REJECTED ->
                        "the module failed to initialize (its .wasm sibling may be missing)"
                },
            )
            append(
                ". The web bundle may lack WASM for this format; rebuild it with the " +
                    "Emscripten toolchain and verify /wasm/ is in the distribution.",
            )
            if (!detail.isNullOrBlank()) append(" Details: $detail")
        }

    companion object {
        /**
         * Classification for the observation the inject/settle phase can make before a promise
         * exists: the script loaded but its factory global is not callable. A script `error` event
         * is classified directly by the loader's listener, so there is no second flag here. A
         * loaded script whose factory is callable is not a failure (`null`).
         */
        fun failureKind(factoryIsFunction: Boolean): WasmLoadFailureKind? =
            if (factoryIsFunction) null else WasmLoadFailureKind.FACTORY_MISSING
    }
}

enum class WasmLoadFailureKind {
    /** The script/module did not settle within the loader's deadline. */
    TIMEOUT,

    /** The injected `<script>` fired `error` (network failure, 404, CSP, …). */
    SCRIPT_ERROR,

    /** The script fired `load` but the expected factory global is not a function. */
    FACTORY_MISSING,

    /** The factory was called and its promise rejected. */
    FACTORY_REJECTED,

    /** The module promise rejected while initializing the `.wasm` blob. */
    MODULE_REJECTED,
}
