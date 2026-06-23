package net.sigmabeta.chipbox.features.playlistdetail

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class PlaylistDetailAction : ChipboxAction() {
    /** Play the whole playlist from the top. */
    data object PlayAllClicked : PlaylistDetailAction()

    /** Shuffle-play the whole playlist. */
    data object ShuffleClicked : PlaylistDetailAction()

    /** Play the playlist starting from the tapped track (view mode). */
    data class TrackClicked(val position: Int) : PlaylistDetailAction()

    /** Enter edit mode (drag-reorder + remove + manage). */
    data object EditClicked : PlaylistDetailAction()

    /** Leave edit mode, back to the read-only view. */
    data object DoneClicked : PlaylistDetailAction()

    /** Rename CTA — stubbed for now (a later slice wires real text entry). */
    data object RenameClicked : PlaylistDetailAction()

    /** Delete the whole playlist and leave the screen. */
    data object DeleteClicked : PlaylistDetailAction()

    /** Remove one track (edit mode only). Reordering is handled via [net.sigmabeta.sage.appcomm.SageAction.Reorder]. */
    data class TrackRemoved(val trackId: Long) : PlaylistDetailAction()
}
