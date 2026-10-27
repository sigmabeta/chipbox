package net.sigmabeta.chipbox.server.http

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.sigmabeta.chipbox.models.HealthStatus
import net.sigmabeta.chipbox.models.ScanState
import net.sigmabeta.chipbox.models.ScanStatus
import net.sigmabeta.chipbox.scanner.state.ScanFailureKind
import net.sigmabeta.chipbox.scanner.state.ScannerState

/**
 * `/api/health` exposes the scanner's live state to the browser. The browser polls it while its
 * first library read comes back empty (see the shared `ScanRetryPolicy`) so opening the app during
 * the startup scan still lands on the scanned library once the scan finishes.
 *
 * `ok` is meaningful: it is false only when the scan ended in a fatal failure. The endpoint is
 * unauthenticated, so nothing host-specific may leak — only the failed file's basename.
 */
internal fun Route.healthRoutes(currentState: () -> ScannerState) {
    get("/api/health") {
        call.respond(currentState().toHealthStatus())
    }
}

internal fun ScannerState.toHealthStatus(): HealthStatus = when (this) {
    ScannerState.Unknown -> HealthStatus(ok = true, state = ScanState(ScanStatus.UNKNOWN))

    ScannerState.Idle -> HealthStatus(ok = true, state = ScanState(ScanStatus.IDLE))

    is ScannerState.Scanning -> HealthStatus(
        ok = true,
        state = ScanState(ScanStatus.SCANNING, timeInSeconds = timeInSeconds),
        games = gamesFound,
        tracks = tracksFound,
        tracksFailed = tracksFailed,
    )

    is ScannerState.Complete -> HealthStatus(
        ok = true,
        state = ScanState(ScanStatus.COMPLETE, timeInSeconds = timeInSeconds),
        games = gamesFound,
        tracks = tracksFound,
        tracksFailed = tracksFailed,
    )

    is ScannerState.Failed -> HealthStatus(
        ok = false,
        // `failedFile` means a file, so a configuration failure has none. For a file failure it is
        // the basename only, and only in the one place the wire contract exposes it: a public
        // endpoint must not echo `/music/…` or a user directory.
        state = ScanState(
            ScanStatus.FAILED,
            failedFile = path.basename().takeIf { kind == ScanFailureKind.FILE },
        ),
    )
}

/** Basename only: the health body is public, so never echo `/music/…` or a user directory. */
private fun String.basename(): String = substringAfterLast('/').substringAfterLast('\\')
