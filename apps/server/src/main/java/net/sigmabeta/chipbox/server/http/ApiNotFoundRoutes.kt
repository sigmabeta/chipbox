package net.sigmabeta.chipbox.server.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import net.sigmabeta.chipbox.models.ErrorResponse

/**
 * Catch-all for unmatched `/api` paths.
 *
 * Registered after every real API route and **before** [staticRoutes]: without it, the SPA tailcard
 * answers an unknown API path with `index.html` (HTTP 200, `text/html`), and the browser reports a
 * JSON decode failure instead of "Not found". A tailcard under `/api` is more specific than the
 * root static tailcard, so real routes (`/api/games`, `/api/files/{id}`, …) still win by route
 * quality; only requests that match nothing reach this handler.
 *
 * `handle` (not `get`) covers every method, so `POST /api/typo` is a JSON 404 too.
 */
internal fun Route.apiNotFoundRoutes() {
    route("/api") {
        route("{...}") {
            handle {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
            }
        }
    }
}
