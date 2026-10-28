package net.sigmabeta.chipbox.scanner.state

/**
 * Why a scan ended in [ScannerState.Failed].
 *
 * [FILE] is a read failure for one file: [ScannerState.Failed.path] is the file name that failed
 * and the UI renders it as "Failed at <file>".
 *
 * [CONFIGURATION] is a bad library configuration (e.g. overlapping roots). There is no failing
 * file, so [ScannerState.Failed.path] names the problem instead — never a host path — and surfaces
 * that need a file (`/api/health`'s `failedFile`, the "Failed at" rows) treat it as absent.
 */
enum class ScanFailureKind {
    FILE,
    CONFIGURATION,
}

sealed class ScannerState {
    object Unknown : ScannerState()

    object Idle : ScannerState()

    // Same fields as [Complete] so the rescan-status screen can show live running totals while a
    // scan is in flight. The scanner emits an updated Scanning as each folder finishes.
    data class Scanning(
        val timeInSeconds: Int = 0,
        val gamesFound: Int = 0,
        val tracksFound: Int = 0,
        val tracksFailed: Int = 0,
        // Streamed-audio files the scan could not read: no Kotlin reader for the extension, or the
        // registered reader found no supported stream in the file. Trailing and defaulted so older
        // call sites/tests keep compiling.
        val filesSkipped: Int = 0
    ) : ScannerState()

    data class Complete(
        val timeInSeconds: Int,
        val gamesFound: Int,
        val tracksFound: Int,
        val tracksFailed: Int,
        val filesSkipped: Int = 0
    ) : ScannerState()

    data class Failed(
        val path: String,
        val kind: ScanFailureKind = ScanFailureKind.FILE,
    ) : ScannerState()
}
