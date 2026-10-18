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
import io.ktor.server.routing.routing
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.server.di.ServerChipboxGraph
import net.sigmabeta.chipbox.server.http.ErrorResponse
import net.sigmabeta.chipbox.server.http.fileRoutes
import net.sigmabeta.chipbox.server.http.healthRoutes
import net.sigmabeta.chipbox.server.http.libraryRoutes
import net.sigmabeta.chipbox.server.http.staticRoutes
import okio.Path.Companion.toOkioPath

/**
 * Boots the Metro graph, kicks off a background scan, and starts Ktor. The server starts serving
 * immediately — endpoints return whatever is in the DB *now*, which on first run is empty until
 * the scan finishes. RealScanner's folder-signature skip-unchanged optimization makes second-run
 * startup near-instant; blocking on first-run scan would be terrible UX. `/api/health` exposes the
 * live scan state so the browser can re-read the library when the scan completes.
 */
@Suppress("LongMethod")
fun main() {
    val config = ServerConfig.fromEnv()
    println("chipbox-server: starting on http://${config.bindHost}:${config.port}")
    println("chipbox-server: library = ${config.libraryDir.absolutePath}")
    println("chipbox-server: db      = ${config.dbPath.absolutePath}")
    println("chipbox-server: no authentication — use on loopback or a trusted LAN only.")
    if (!ServerConfig.isLoopbackHost(config.bindHost)) {
        System.err.println(
            "chipbox-server: WARNING: binding ${config.bindHost} exposes the library to every host " +
                "that can reach this port, with no authentication. Bind 127.0.0.1 (or put a reverse " +
                "proxy with auth in front) unless you trust the whole network.",
        )
    }

    val graph = createGraphFactory<ServerChipboxGraph.Factory>().create(
        dbPath = config.dbPath.absolutePath,
        workDir = config.workDir,
    )
    // Seed the configured library directory into the persisted locations list. Idempotent —
    // LocalFileContentSource dedups against `library-locations.txt`.
    graph.localFileContentSource.addLocation(config.libraryDir.toOkioPath())

    // The latest scanner state, not just a `scanning` boolean: `/api/health` reports the real
    // Idle/Scanning/Complete/Failed state with its counters so the browser can wait for the
    // startup scan and operators can see why a scan failed. The flow starts with the scanner's
    // current state, so this subscription also catches a scan that started before it attached.
    val scannerState = AtomicReference<ScannerState>(ScannerState.Unknown)
    graph.appScope.launch {
        graph.scanner.state().collect(scannerState::set)
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
            healthRoutes { scannerState.get() }
            libraryRoutes(graph.repository)
            fileRoutes(graph.repository, graph.contentSources)
            // Must come AFTER the /api routes so the SPA fallback doesn't shadow JSON endpoints.
            staticRoutes()
        }
    }.start(wait = true)
}
