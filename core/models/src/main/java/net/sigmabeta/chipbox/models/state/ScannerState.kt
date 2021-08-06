package net.sigmabeta.chipbox.models.state

sealed class ScannerState {
    object Unknown : ScannerState()

    object Idle : ScannerState()

    object Scanning : ScannerState()

    data class Complete(
        val timeInSeconds: Int,
        val gamesFound: Int,
        val tracksFound: Int,
        val tracksFailed: Int
    ) : ScannerState()
}