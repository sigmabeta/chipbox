package net.sigmabeta.chipbox.server

import dev.zacsweers.metro.createGraphFactory
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.server.di.ServerChipboxGraph
import net.sigmabeta.chipbox.server.http.ErrorResponse
import net.sigmabeta.chipbox.server.http.fileRoutes
import net.sigmabeta.chipbox.server.http.libraryRoutes
import net.sigmabeta.chipbox.server.http.staticRoutes

/**
 * Boots the Metro graph, kicks off a background scan, and starts Ktor. The server starts serving
 * immediately — endpoints return whatever is in the DB *now*, which on first run is empty until
 * the scan finishes. RealScanner's folder-signature skip-unchanged optimization makes second-run
 * startup near-instant; blocking on first-run scan would be terrible UX.
 *
 * Static apps/js bundle serving lands in Phase F.
 */
fun main() {
    val config = ServerConfig.fromEnv()
    println("chipbox-server: starting on http://${config.bindHost}:${config.port}")
    println("chipbox-server: library = ${config.libraryDir.absolutePath}")
    println("chipbox-server: db      = ${config.dbPath.absolutePath}")

    val graph = createGraphFactory<ServerChipboxGraph.Factory>().create(
        dbPath = config.dbPath.absolutePath,
        workDir = config.workDir,
    )
    // Seed the configured library directory into the persisted locations list. Idempotent —
    // LocalFileContentSource dedups against `library-locations.txt`.
    graph.localFileContentSource.addLocation(config.libraryDir)

    val scanning = AtomicBoolean(false)
    graph.appScope.launch {
        graph.scanner.state().collect { state ->
            scanning.set(state is ScannerState.Scanning)
        }
    }
    graph.scanner.startScan()

    embeddedServer(Netty, port = config.port, host = config.bindHost) {
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
        install(CallLogging)
        if (config.corsAllowedOrigins.isNotEmpty()) {
            install(CORS) {
                config.corsAllowedOrigins.forEach { origin ->
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
                allowMethod(io.ktor.http.HttpMethod.Get)
                allowMethod(io.ktor.http.HttpMethod.Post)
                allowMethod(io.ktor.http.HttpMethod.Delete)
            }
        }
        routing {
            get("/api/health") {
                call.respond(
                    HealthResponse(
                        ok = true,
                        scanning = scanning.get(),
                        libraryDir = config.libraryDir.absolutePath,
                    ),
                )
            }
            libraryRoutes(graph.repository)
            fileRoutes(graph.repository, graph.contentSources)
            // Must come AFTER the /api routes so the SPA fallback doesn't shadow JSON endpoints.
            staticRoutes()
        }
    }.start(wait = true)
}

@Serializable
private data class HealthResponse(
    val ok: Boolean,
    val scanning: Boolean,
    val libraryDir: String,
)
