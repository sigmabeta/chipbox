package net.sigmabeta.chipbox.common.ui.components.api

import kotlinx.collections.immutable.ImmutableList
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ListModel

/** Coarse scan outcome, used to pick the card's background colour. */
enum class ScanCardStatus { SCANNING, COMPLETE, FAILED }

/** What the card's scrolling right column shows. */
sealed interface ScanStatusDetail {
    /** A list of pre-built rows (live changes while scanning, or the final summary). */
    data class Rows(val items: ImmutableList<ListModel>) : ScanStatusDetail

    /** A free-form, wrapping failure message. */
    data class Error(val message: String) : ScanStatusDetail
}

/**
 * A fixed-size Home card that mirrors the Rescan Status screen at a glance while a scan is running
 * (or has just finished). Two columns: the left shows the coarse status and — while scanning — the
 * folder and file being read; the right vertically scrolls the [detail] (live changes, final
 * summary, or the failure message).
 *
 * The card paints its own bounded, backgrounded container (max width + fixed height) whose colour
 * tracks [status] — neutral while scanning, then a success/error tint on completion/failure — so it
 * doesn't need a wrapping section.
 */
data class ScanStatusCardListModel(
    val status: ScanCardStatus,
    val statusLabel: String,
    // The folder being walked right now, shown above [currentFile] as the coarser scan context.
    // Scanning-only, like [currentFile]; null when not scanning or before the first folder event.
    val currentFolder: String? = null,
    val currentFile: String?,
    val detail: ScanStatusDetail,
    // When non-null, a tap anywhere on the card dispatches this — used to dismiss the card once the
    // scan has settled (complete/failed). Null while scanning, so in-progress taps don't dismiss.
    val dismissAction: SageAction? = null,
) : ListModel() {
    // Single-instance card — only one scan status on Home at a time.
    override val dataId: Long = DATA_ID
    override val columns: Int = ListModel.COLUMNS_ALL

    private companion object {
        const val DATA_ID = -90_001L
    }
}
