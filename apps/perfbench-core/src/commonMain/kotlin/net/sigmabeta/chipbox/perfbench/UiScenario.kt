package net.sigmabeta.chipbox.perfbench

/**
 * The canonical UI scenarios the device benchmark measures.
 *
 * The [id] is the scenario key stored in a [UiResult] and matched by a baseline diff; it is stable
 * across runs and devices. Each scenario scrolls one core surface: the BrowseByGame grid, Home's
 * vertical list, the BrowseAllTracks list, and the artist-detail list.
 */
enum class UiScenario(val id: String) {
    BROWSE_BY_GAME_SCROLL("ui.browse_by_game_scroll"),
    HOME_SCROLL("ui.home_scroll"),
    BROWSE_ALL_TRACKS_SCROLL("ui.browse_all_tracks_scroll"),
    ARTIST_DETAIL_SCROLL("ui.artist_detail_scroll"),
    ;

    companion object {
        /** Every scenario, in the order the harness reports them. */
        val all: List<UiScenario> = entries.toList()

        /** Resolves an [id] such as `ui.browse_by_game_scroll`, or `null` if it names no scenario. */
        fun fromId(id: String): UiScenario? = entries.firstOrNull { it.id == id }
    }
}
