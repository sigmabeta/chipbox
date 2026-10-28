package net.sigmabeta.chipbox.server.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondResource
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route

/**
 * Serves the apps/js webpack bundle off the runtime classpath. The Gradle `copyJsBundle` task
 * (see apps/server/build.gradle.kts) drops the bundle into `build/generated/static/static/`,
 * which gets picked up as a resource source root → resolves at the classpath prefix `static/`.
 *
 * `staticResources("/", "static")` exposes `static/<name>` resources at `/<name>`.
 *
 * **Fallback policy** (`fallback`, not `default("index.html")`): the SPA needs `index.html` for
 * client-side routes, but a *missing asset* must be a real 404. Serving HTML for an absent
 * `/wasm/…js` makes the browser parse markup as JavaScript (console SyntaxError), the Emscripten
 * factory global never appears, and playback stalls without an actionable error. The injected
 * `<script>`'s `error` listener only fires on a non-2xx response, so 404 is what lets the WASM
 * loader report "the bundle may lack WASM". The same rule applies to any asset-shaped path:
 * anything under `/wasm/` or with a filename extension is a file, not an app route.
 *
 * `/api/…` is deliberately not handled here — its JSON 404 catch-all belongs to the API routes,
 * which MUST be installed before this fallback so they win for unknown endpoints.
 */
internal fun Route.staticRoutes() {
    staticResources("/", "static") {
        fallback { requestedPath, call ->
            if (requestedPath.looksLikeStaticAsset()) {
                call.respondText("Not found", status = HttpStatusCode.NotFound)
            } else {
                // `respondResource` resolves against the classpath; the bundle always ships its
                // index. A server run without the web bundle (plain `:apps:server:run` with the
                // dev server on :8081) still 404s here instead of throwing a 500.
                val index = call.application.environment.classLoader
                    .getResource("static/index.html")
                if (index == null) {
                    call.respondText(
                        "Web bundle not installed",
                        status = HttpStatusCode.NotFound,
                    )
                } else {
                    call.respondResource("static/index.html")
                }
            }
        }
    }
}

/**
 * True when a request is asking for a file rather than an app route. Paths arrive with the
 * platform separator (Ktor joins the tailcard with `File.separator`) — normalize first.
 */
private fun String.looksLikeStaticAsset(): Boolean {
    val path = replace('\\', '/')
    if (path.startsWith("wasm/") || path == "wasm") return true
    val filename = path.substringAfterLast('/')
    return filename.contains('.')
}
