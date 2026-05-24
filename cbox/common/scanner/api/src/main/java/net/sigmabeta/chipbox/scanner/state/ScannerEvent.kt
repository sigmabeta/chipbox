package net.sigmabeta.chipbox.scanner.state

sealed class ScannerEvent {
    object Unknown : ScannerEvent()

    /** A new game was added to the library this scan. [id] is its repository id; [imageUrl] is its
     *  cover-art source, or null when the game has no artwork. */
    class GameFoundEvent(
        val id: Long,
        val name: String,
        val trackCount: Int,
        val imageUrl: String?
    ) : ScannerEvent()

    /** An existing game was re-scanned and something meaningfully changed (tracks/metadata). */
    class GameUpdated(
        val id: Long,
        val name: String,
        val trackCount: Int,
        val imageUrl: String?
    ) : ScannerEvent()

    /** A game's folder is gone, so the game was pruned from the library this scan. */
    class GameRemoved(
        val name: String
    ) : ScannerEvent()
}
