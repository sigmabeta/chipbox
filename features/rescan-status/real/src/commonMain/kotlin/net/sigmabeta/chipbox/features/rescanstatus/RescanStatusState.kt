package net.sigmabeta.chipbox.features.rescanstatus

import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ImageNameCaptionListModel
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

enum class ScanPhase { IDLE, SCANNING, COMPLETE, FAILED }

enum class ScanEventKind { ADDED, UPDATED, REMOVED }

/**
 * One scanner change, projected for the list. [id] is monotonic so rows stay keyed/stable;
 * [gameId] is the repository id for navigation (null for removed games, which no longer exist);
 * [imageUrl] is the game's cover art source.
 */
data class ScanEventItem(
    val id: Long,
    val gameName: String,
    val kind: ScanEventKind,
    val trackCount: Int,
    val gameId: Long?,
    val imageUrl: String?,
)

data class RescanStatusState(
    val phase: ScanPhase = ScanPhase.IDLE,
    val timeInSeconds: Int = 0,
    val gamesFound: Int = 0,
    val tracksFound: Int = 0,
    val tracksFailed: Int = 0,
    val failedPath: String? = null,
    // The file the scanner is reading right now, surfaced under Progress as a live heartbeat so the
    // screen shows motion between (rare) meaningful changes. Updated on a much faster cadence than
    // [events] — which stays batched for perf — and is never mixed into that Changes list. Null
    // when no file is currently being read.
    val currentFile: String? = null,
    val events: List<ScanEventItem> = emptyList(),
) : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.RESCAN_STATUS_TITLE),
        shouldShowBack = true,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> =
        progressItems(stringProvider) + eventItems(stringProvider)

    private fun progressItems(stringProvider: StringProvider): List<ListModel> {
        if (phase == ScanPhase.IDLE) {
            return listOf(
                EmptyStateListModel(
                    icon = Icon.MusicNote,
                    explanation = stringProvider.getString(ChipboxStringId.RESCAN_STATUS_IDLE),
                    showCrossOut = false,
                ),
            )
        }
        val rows = mutableListOf<ListModel>(
            SectionHeaderListModel(stringProvider.getString(ChipboxStringId.RESCAN_STATUS_SECTION_PROGRESS)),
            labelValue(stringProvider, ChipboxStringId.RESCAN_STATUS_LABEL_ELAPSED, timeInSeconds),
            labelValue(stringProvider, ChipboxStringId.RESCAN_STATUS_LABEL_GAMES, gamesFound),
            labelValue(stringProvider, ChipboxStringId.RESCAN_STATUS_LABEL_TRACKS, tracksFound),
            labelValue(stringProvider, ChipboxStringId.RESCAN_STATUS_LABEL_FAILED, tracksFailed),
        )
        // Live per-file heartbeat: only meaningful mid-scan, and shown as a plain label/value row
        // (it's a filename, not a game) so it can never be confused with a Changes entry.
        if (phase == ScanPhase.SCANNING && currentFile != null) {
            rows += LabelValueListModel(
                label = stringProvider.getString(ChipboxStringId.RESCAN_STATUS_LABEL_SCANNING),
                value = currentFile,
                clickAction = SageAction.Noop,
            )
        }
        if (phase == ScanPhase.FAILED && failedPath != null) {
            rows += LabelValueListModel(
                label = stringProvider.getString(ChipboxStringId.RESCAN_STATUS_LABEL_FAILED_PATH),
                value = failedPath,
                clickAction = SageAction.Noop,
            )
        }
        return rows
    }

    private fun eventItems(stringProvider: StringProvider): List<ListModel> {
        if (events.isEmpty()) return emptyList()
        return buildList {
            add(SectionHeaderListModel(stringProvider.getString(ChipboxStringId.RESCAN_STATUS_SECTION_EVENTS)))
            // [events] is chronological; reverse so the most recent change shows at the top.
            events.asReversed().forEach { event -> add(eventRow(stringProvider, event)) }
        }
    }

    private fun eventRow(stringProvider: StringProvider, event: ScanEventItem) =
        ImageNameCaptionListModel(
            dataId = event.id,
            name = event.gameName,
            caption = if (event.kind == ScanEventKind.REMOVED) {
                stringProvider.getString(event.kind.labelId())
            } else {
                "${stringProvider.getString(event.kind.labelId())} · ${event.trackCount} tracks"
            },
            sourceInfo = SourceInfo(info = event.imageUrl),
            imagePlaceholder = Icon.Album,
            // Added/updated games open their detail screen; a removed game is gone, so no tap.
            clickAction = event.gameId?.let { RescanStatusAction.GameClicked(it) } ?: SageAction.Noop,
        )

    private fun labelValue(stringProvider: StringProvider, labelId: ChipboxStringId, value: Int) =
        LabelValueListModel(
            label = stringProvider.getString(labelId),
            value = value.toString(),
            clickAction = SageAction.Noop,
        )

    private fun ScanEventKind.labelId(): ChipboxStringId = when (this) {
        ScanEventKind.ADDED -> ChipboxStringId.RESCAN_STATUS_EVENT_ADDED
        ScanEventKind.UPDATED -> ChipboxStringId.RESCAN_STATUS_EVENT_UPDATED
        ScanEventKind.REMOVED -> ChipboxStringId.RESCAN_STATUS_EVENT_REMOVED
    }
}
