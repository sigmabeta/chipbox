package net.sigmabeta.chipbox.features.search.real

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class SearchAction : ChipboxAction() {
    /** Text field changed (also fired with "" by the clear button). */
    data class QueryChanged(val query: String) : SearchAction()

    /** A history row was tapped — refill the search box with that query. */
    data class HistoryClicked(val query: String) : SearchAction()

    /** The X on a history row — drop that entry from history. */
    data class HistoryRemoved(val id: Long) : SearchAction()

    /** A game result row was tapped — open its detail screen. */
    data class GameClicked(val gameId: Long) : SearchAction()

    /** A song result row was tapped — play the song results as a setlist, starting here. */
    data class SongClicked(val trackId: Long) : SearchAction()

    /** An artist result row was tapped — open its detail screen. */
    data class ArtistClicked(val artistId: Long) : SearchAction()

    data object BackClicked : SearchAction()
}
