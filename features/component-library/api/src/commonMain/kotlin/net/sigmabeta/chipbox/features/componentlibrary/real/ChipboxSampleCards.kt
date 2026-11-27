package net.sigmabeta.chipbox.features.componentlibrary.real

import kotlinx.collections.immutable.persistentListOf
import net.sigmabeta.chipbox.common.ui.components.api.NowPlayingHomeCardListModel
import net.sigmabeta.chipbox.common.ui.components.api.ScanCardStatus
import net.sigmabeta.chipbox.common.ui.components.api.ScanStatusCardListModel
import net.sigmabeta.chipbox.common.ui.components.api.ScanStatusDetail
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.NameCaptionListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.componentlibrary.componentLibraryExtraId

/**
 * Chipbox's own cards for the gallery's "App rows" section: the Home now-playing card (playing,
 * paused, buffering, error) and the scan-status card (scanning, complete, failed). Text is plain
 * English: this is a debug-only surface. Artwork is a synthetic [SourceInfo]; the host forces preview
 * art. Every id comes from [componentLibraryExtraId], which can't collide with the catalog's.
 */
fun chipboxSampleCards(): List<ListModel> = nowPlayingCards() + scanStatusCards()

private fun nowPlayingCards(): List<ListModel> = listOf(
    nowPlaying(index = 0, title = "Playing", isPlaying = true, progress = 0.4f),
    nowPlaying(index = 1, title = "Paused", progress = 0.75f),
    nowPlaying(index = 2, title = "Buffering", isBuffering = true),
    nowPlaying(index = 3, title = "Error", isError = true),
)

private fun nowPlaying(
    index: Int,
    title: String,
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    isError: Boolean = false,
    progress: Float = 0f,
) = NowPlayingHomeCardListModel(
    title = title,
    artistsCaption = "Howard Drossin, Brad Buxer",
    artwork = SourceInfo("component-library/now-playing/$index"),
    isPlaying = isPlaying,
    isBuffering = isBuffering,
    isError = isError,
    progressFraction = progress,
    clickAction = SageAction.Noop,
    playPauseAction = SageAction.Noop,
    appearAction = SageAction.Noop,
    disappearAction = SageAction.Noop,
    dataId = componentLibraryExtraId(NOW_PLAYING_BASE + index),
)

private fun scanStatusCards(): List<ListModel> = listOf(
    ScanStatusCardListModel(
        status = ScanCardStatus.SCANNING,
        statusLabel = "Library Scan Scanning",
        currentFolder = "Sonic the Hedgehog",
        currentFile = "robotnik_theme.spc",
        detail = ScanStatusDetail.Rows(persistentListOf(changeRow(0, "Added green_hill.spc"))),
        dataId = componentLibraryExtraId(SCAN_STATUS_BASE),
    ),
    ScanStatusCardListModel(
        status = ScanCardStatus.COMPLETE,
        statusLabel = "Library Scan Complete",
        currentFile = null,
        detail = ScanStatusDetail.Rows(persistentListOf(changeRow(1, "12 tracks added"))),
        dismissAction = SageAction.Noop,
        dataId = componentLibraryExtraId(SCAN_STATUS_BASE + 1),
    ),
    ScanStatusCardListModel(
        status = ScanCardStatus.FAILED,
        statusLabel = "Library Scan Failed",
        currentFile = null,
        detail = ScanStatusDetail.Error("The scan failed while reading bad-folder/track.spc."),
        dismissAction = SageAction.Noop,
        dataId = componentLibraryExtraId(SCAN_STATUS_BASE + 2),
    ),
)

private fun changeRow(index: Int, text: String): ListModel = NameCaptionListModel(
    dataId = componentLibraryExtraId(SCAN_ROW_BASE + index),
    name = text,
    caption = "",
    clickAction = SageAction.Noop,
)

private const val NOW_PLAYING_BASE = 0
private const val SCAN_STATUS_BASE = 10
private const val SCAN_ROW_BASE = 20
