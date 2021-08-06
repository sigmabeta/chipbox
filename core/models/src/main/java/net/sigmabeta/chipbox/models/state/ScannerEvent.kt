package net.sigmabeta.chipbox.models.state

sealed class ScannerEvent {
    object Unknown : ScannerEvent()

    class GameFoundEvent(
        val name: String,
        val trackCount: Int,
        val imageUrl: String
    ) : ScannerEvent()
}