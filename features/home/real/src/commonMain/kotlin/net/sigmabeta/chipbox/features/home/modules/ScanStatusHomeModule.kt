package net.sigmabeta.chipbox.features.home.modules

import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.scan
import net.sigmabeta.chipbox.common.ui.components.api.ScanCardStatus
import net.sigmabeta.chipbox.common.ui.components.api.ScanStatusCardListModel
import net.sigmabeta.chipbox.common.ui.components.api.ScanStatusDetail
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.state.ScannerEvent
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ImageNameCaptionListModel
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * Surfaces the live state of a library scan as a Home card. Hidden when the scanner is idle; while a
 * scan runs (or once it finishes/fails) the card shows the coarse status plus the file being read,
 * and a scrolling detail column — the running changes during a scan, the summary on completion, or
 * the failure message.
 *
 * The scanner's per-file heartbeat fires far too often to render each one, so the reduced state is
 * [sample]d down to [REFRESH_MS]; the rare meaningful changes accumulate in between and are carried
 * through every emission, so none are lost.
 */
class ScanStatusHomeModule @Inject constructor(
    private val scanner: Scanner,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    // The card carries its own status header, so the section header would just duplicate it.
    override val showHeader = false

    @OptIn(FlowPreview::class)
    override fun state(): Flow<LCE<HomeModuleSection>> =
        merge(
            scanner.state().map<ScannerState, Input> { Input.State(it) },
            scanner.scanEvents().map<ScannerEvent, Input> { Input.Event(it) },
        )
            .scan(Accumulator()) { acc, input -> acc.reduce(input) }
            .sample(REFRESH_MS)
            .map { render(it) }
            .distinctUntilChanged()

    private fun render(acc: Accumulator): LCE<HomeModuleSection> {
        val status = acc.phase.toCardStatus() ?: return LCE.Uninitialized
        // "Library Scan Scanning/Complete/Failed" — the fixed title prefixes the coarse status word.
        val statusLabel = stringProvider.getStringOneArg(
            ChipboxStringId.HOME_SCAN_TITLE,
            stringProvider.getString(status.labelId()),
        )
        val card = ScanStatusCardListModel(
            status = status,
            statusLabel = statusLabel,
            // The folder/file marquees are only meaningful while actively scanning.
            currentFolder = acc.currentFolder.takeIf { status == ScanCardStatus.SCANNING },
            currentFile = acc.currentFile.takeIf { status == ScanCardStatus.SCANNING },
            detail = detail(acc, status),
            // A settled scan can be dismissed by tapping the card; an in-progress one can't.
            dismissAction = HomeAction.ScanStatusDismissed.takeIf { status != ScanCardStatus.SCANNING },
        )
        return LCE.Content(HomeModuleSection(title = statusLabel, items = persistentListOf(card)))
    }

    private fun detail(acc: Accumulator, status: ScanCardStatus): ScanStatusDetail = when (status) {
        // Newest change first, mirroring the Rescan Status screen.
        ScanCardStatus.SCANNING -> ScanStatusDetail.Rows(acc.changes.asReversed().map { changeRow(it) }.toImmutableList())

        ScanCardStatus.COMPLETE -> ScanStatusDetail.Rows(summaryRows(acc).toImmutableList())

        ScanCardStatus.FAILED -> ScanStatusDetail.Error(errorMessage(acc))
    }

    private fun changeRow(change: Change) = ImageNameCaptionListModel(
        dataId = change.id,
        name = change.name,
        caption = if (change.kind == ChangeKind.REMOVED) {
            stringProvider.getString(change.kind.labelId())
        } else {
            "${stringProvider.getString(change.kind.labelId())} · ${change.trackCount} tracks"
        },
        sourceInfo = SourceInfo(info = change.imageUrl),
        imagePlaceholder = Icon.Album,
        // Added/updated games open their detail screen; a removed game is gone, so no tap.
        clickAction = change.gameId?.let { HomeAction.GameClicked(it) } ?: SageAction.Noop,
    )

    private fun summaryRows(acc: Accumulator): List<ListModel> = listOf(
        labelValue(ChipboxStringId.RESCAN_STATUS_LABEL_ELAPSED, acc.timeInSeconds),
        labelValue(ChipboxStringId.RESCAN_STATUS_LABEL_GAMES, acc.gamesFound),
        labelValue(ChipboxStringId.RESCAN_STATUS_LABEL_TRACKS, acc.tracksFound),
        labelValue(ChipboxStringId.RESCAN_STATUS_LABEL_FAILED, acc.tracksFailed),
    )

    private fun errorMessage(acc: Accumulator): String = acc.failedPath
        ?.let { stringProvider.getStringOneArg(ChipboxStringId.HOME_SCAN_STATUS_ERROR, it) }
        ?: stringProvider.getString(ChipboxStringId.HOME_SCAN_STATUS_ERROR_GENERIC)

    private fun labelValue(labelId: ChipboxStringId, value: Int) = LabelValueListModel(
        label = stringProvider.getString(labelId),
        value = value.toString(),
        clickAction = SageAction.Noop,
    )

    private companion object {
        const val ID = "scan_status"

        // Above Game of the Day (100) but below the Now Playing card (0): prominent while a scan
        // runs, without displacing active playback.
        const val PRIORITY = 50

        // The scanner's file heartbeat fires per file (thousands a scan); throttle the card to a
        // calm cadence so Home doesn't recompose on every read.
        const val REFRESH_MS = 500L
    }
}

private fun ScanPhase.toCardStatus(): ScanCardStatus? = when (this) {
    ScanPhase.SCANNING -> ScanCardStatus.SCANNING
    ScanPhase.COMPLETE -> ScanCardStatus.COMPLETE
    ScanPhase.FAILED -> ScanCardStatus.FAILED
    ScanPhase.IDLE -> null
}

private fun ScanCardStatus.labelId(): ChipboxStringId = when (this) {
    ScanCardStatus.SCANNING -> ChipboxStringId.HOME_SCAN_STATUS_SCANNING
    ScanCardStatus.COMPLETE -> ChipboxStringId.HOME_SCAN_STATUS_COMPLETE
    ScanCardStatus.FAILED -> ChipboxStringId.HOME_SCAN_STATUS_FAILED
}

private fun ChangeKind.labelId(): ChipboxStringId = when (this) {
    ChangeKind.ADDED -> ChipboxStringId.RESCAN_STATUS_EVENT_ADDED
    ChangeKind.UPDATED -> ChipboxStringId.RESCAN_STATUS_EVENT_UPDATED
    ChangeKind.REMOVED -> ChipboxStringId.RESCAN_STATUS_EVENT_REMOVED
}

private enum class ScanPhase { IDLE, SCANNING, COMPLETE, FAILED }

private enum class ChangeKind { ADDED, UPDATED, REMOVED }

private class Change(
    val id: Long,
    val name: String,
    val kind: ChangeKind,
    val trackCount: Int,
    val gameId: Long?,
    val imageUrl: String?,
)

private sealed interface Input {
    data class State(val state: ScannerState) : Input
    data class Event(val event: ScannerEvent) : Input
}

/**
 * Mutable running reduction of the two scanner flows. A single instance is threaded through [scan];
 * it's read (and snapshotted into immutable rows) only when [ScanStatusHomeModule.render] runs on a
 * sampled tick, all on the same dispatcher, so the in-place mutation is safe.
 */
private class Accumulator {
    var phase = ScanPhase.IDLE
    var timeInSeconds = 0
    var gamesFound = 0
    var tracksFound = 0
    var tracksFailed = 0
    var failedPath: String? = null
    var currentFolder: String? = null
    var currentFile: String? = null
    val changes = mutableListOf<Change>()

    private var nextChangeId = 0L

    fun reduce(input: Input): Accumulator {
        when (input) {
            is Input.State -> absorb(input.state)
            is Input.Event -> apply(input.event)
        }
        return this
    }

    private fun absorb(state: ScannerState) {
        when (state) {
            is ScannerState.Scanning -> {
                // Entering Scanning from a settled phase means a fresh run — clear the previous
                // run's file and change list so the card reflects only the current scan.
                if (phase != ScanPhase.SCANNING) reset()
                phase = ScanPhase.SCANNING
                timeInSeconds = state.timeInSeconds
                gamesFound = state.gamesFound
                tracksFound = state.tracksFound
                tracksFailed = state.tracksFailed
                failedPath = null
            }

            is ScannerState.Complete -> {
                phase = ScanPhase.COMPLETE
                timeInSeconds = state.timeInSeconds
                gamesFound = state.gamesFound
                tracksFound = state.tracksFound
                tracksFailed = state.tracksFailed
                failedPath = null
            }

            is ScannerState.Failed -> {
                phase = ScanPhase.FAILED
                failedPath = state.path
            }

            ScannerState.Idle, ScannerState.Unknown -> phase = ScanPhase.IDLE
        }
    }

    private fun apply(event: ScannerEvent) {
        when (event) {
            is ScannerEvent.FolderScanned -> currentFolder = event.name

            is ScannerEvent.FileScanned -> currentFile = event.name

            is ScannerEvent.GameFoundEvent ->
                changes.add(Change(nextChangeId++, event.name, ChangeKind.ADDED, event.trackCount, event.id, event.imageUrl))

            is ScannerEvent.GameUpdated ->
                changes.add(Change(nextChangeId++, event.name, ChangeKind.UPDATED, event.trackCount, event.id, event.imageUrl))

            is ScannerEvent.GameRemoved ->
                changes.add(Change(nextChangeId++, event.name, ChangeKind.REMOVED, 0, gameId = null, imageUrl = null))

            ScannerEvent.Unknown -> Unit
        }
    }

    private fun reset() {
        currentFolder = null
        currentFile = null
        changes.clear()
    }
}
