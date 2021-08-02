package net.sigmabeta.chipbox.scanner

sealed class ScannerEvent

class GameFoundEvent(
    val name: String,
    val trackCount: Int,
    val imageUrl: String
) : ScannerEvent()
