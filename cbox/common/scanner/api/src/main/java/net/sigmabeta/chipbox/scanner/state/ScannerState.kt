package net.sigmabeta.chipbox.scanner.state

sealed class ScannerState {
    object Unknown : ScannerState()

    object Idle : ScannerState()

    // Same fields as [Complete] so the rescan-status screen can show live running totals while a
    // scan is in flight. The scanner emits an updated Scanning as each folder finishes.
    data class Scanning(
        val timeInSeconds: Int = 0,
        val gamesFound: Int = 0,
        val tracksFound: Int = 0,
        val tracksFailed: Int = 0
    ) : ScannerState()

    data class Complete(
        val timeInSeconds: Int,
        val gamesFound: Int,
        val tracksFound: Int,
        val tracksFailed: Int
    ) : ScannerState()

    data class Failed(
        val path: String
    ) : ScannerState()
}
