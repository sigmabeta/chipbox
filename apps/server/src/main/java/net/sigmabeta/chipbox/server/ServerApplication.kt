package net.sigmabeta.chipbox.server

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import java.io.IOException
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.ErrorResponse
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.server.http.apiNotFoundRoutes
import net.sigmabeta.chipbox.server.http.fileRoutes
import net.sigmabeta.chipbox.server.http.healthRoutes
import net.sigmabeta.chipbox.server.http.libraryRoutes
import net.sigmabeta.chipbox.server.http.staticRoutes

/**
 * The whole Ktor application stack — plugins plus routing — in one place so `main()` and the
 * route tests configure the same server. Nothing here touches the Metro graph, Netty, or the
 * environment, so `testApplication` can mount it over an in-memory repository.
 *
 * Route order is load-bearing: the real `/api` routes come first, then the JSON 404 catch-all for
 * unknown API paths, then the SPA static fallback. Moving [staticRoutes] up would shadow the API;
 * moving [apiNotFoundRoutes] after it would leave unknown API paths answering with HTML.
 */
internal fun Application.configureChipboxServer(
    repository: Repository,
    contentSources: ContentSourceRegistry,
    currentScannerState: () -> ScannerState,
    corsAllowedOrigins: List<String> = emptyList(),
    logCalls: Boolean = true,
) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<IOException> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "I/O error"))
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("${cause::class.simpleName}: ${cause.message ?: "unknown"}"),
            )
        }
    }
    install(PartialContent)
    install(AutoHeadResponse)
    if (logCalls) install(CallLogging)
    if (corsAllowedOrigins.isNotEmpty()) {
        install(CORS) {
            corsAllowedOrigins.forEach { origin ->
                // Ktor wants host without scheme; allowHost normalizes this for us.
                val withoutScheme = origin.substringAfter("://")
                val (host, schemeList) = if (origin.contains("://")) {
                    withoutScheme to listOf(origin.substringBefore("://"))
                } else {
                    origin to listOf("http", "https")
                }
                allowHost(host, schemes = schemeList)
            }
            allowHeader("Content-Type")
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Delete)
        }
    }
    routing {
        healthRoutes(currentScannerState)
        libraryRoutes(repository)
        fileRoutes(repository, contentSources)
        apiNotFoundRoutes()
        // Must come AFTER the /api routes so the SPA fallback doesn't shadow JSON endpoints.
        staticRoutes()
    }
}
