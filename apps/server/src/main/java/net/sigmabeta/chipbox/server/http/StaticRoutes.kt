package net.sigmabeta.chipbox.server.http

import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Route

/**
 * Serves the apps/js webpack bundle off the runtime classpath. The Gradle `copyJsBundle` task
 * (see apps/server/build.gradle.kts) drops the bundle into `build/generated/static/static/`,
 * which gets picked up as a resource source root → resolves at the classpath prefix `static/`.
 *
 * `staticResources("/", "static")` exposes `static/<name>` resources at `/<name>`.
 * `default("index.html")` makes unmatched paths inside the static tree fall back to
 * `index.html` — required for SPA routing where the JS app handles in-app navigation client-side
 * and a hard refresh on `/some/deep/route` would otherwise 404.
 *
 * MUST be installed after the `/api/...` routes so a request to `/api/games` doesn't get
 * shadowed by the SPA fallback.
 */
internal fun Route.staticRoutes() {
    staticResources("/", "static") {
        default("index.html")
    }
}
