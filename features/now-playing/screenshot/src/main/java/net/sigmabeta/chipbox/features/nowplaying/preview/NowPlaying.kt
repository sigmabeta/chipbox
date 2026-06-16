package net.sigmabeta.chipbox.features.nowplaying.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.nowplaying.real.ContextMenuMode
import net.sigmabeta.chipbox.features.nowplaying.real.NowPlayingArtist
import net.sigmabeta.chipbox.features.nowplaying.real.NowPlayingContent
import net.sigmabeta.chipbox.features.nowplaying.real.NowPlayingError
import net.sigmabeta.chipbox.features.nowplaying.real.NowPlayingModel
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ScreenPreview
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.list.WidthClass

private val NoOpSink = ActionSink { }

/** Steady-state playback: the transport shows the pause icon. */
@DevicePreviews
@Composable
internal fun NowPlayingPlaying(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    NowPlayingScreenshot(darkTheme, syntheticWidthClass, sampleModel(isPlaying = true))
}

/** Paused: the transport shows the play icon. */
@DevicePreviews
@Composable
internal fun NowPlayingPaused(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    NowPlayingScreenshot(darkTheme, syntheticWidthClass, sampleModel(isPlaying = false))
}

/** Buffering: a spinner replaces the play/pause icon while buffers fill. */
@DevicePreviews
@Composable
internal fun NowPlayingBuffering(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    NowPlayingScreenshot(
        darkTheme,
        syntheticWidthClass,
        sampleModel(isPlaying = false, isBuffering = true),
    )
}

/** Fatal error: the transport shows a warning icon and the error log lists recent failures. */
@DevicePreviews
@Composable
internal fun NowPlayingError(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    NowPlayingScreenshot(
        darkTheme,
        syntheticWidthClass,
        sampleModel(
            isPlaying = false,
            errorMessage = SAMPLE_ERROR,
            errors = listOf(
                NowPlayingError(id = 0L, message = "Mega Man 2 - Wily 1: $SAMPLE_ERROR"),
                NowPlayingError(id = 1L, message = "Mega Man 2 - Wily 2: file not found"),
            ),
        ),
    )
}

/** Context menu — LINKS: jump-off rows for the playing track's game and (single) artist. */
@DevicePreviews
@Composable
internal fun NowPlayingLinks(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    NowPlayingScreenshot(
        darkTheme,
        syntheticWidthClass,
        sampleModel(
            isPlaying = true,
            contextMenuMode = ContextMenuMode.LINKS,
            artists = listOf(NowPlayingArtist(id = 0L, name = "Takashi Tateishi")),
        ),
    )
}

/** Context menu — ARTISTS: one row per artist for a multi-artist track. */
@DevicePreviews
@Composable
internal fun NowPlayingArtists(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    NowPlayingScreenshot(
        darkTheme,
        syntheticWidthClass,
        sampleModel(
            isPlaying = true,
            contextMenuMode = ContextMenuMode.ARTISTS,
            artists = listOf(
                NowPlayingArtist(id = 0L, name = "Takashi Tateishi"),
                NowPlayingArtist(id = 1L, name = "Manami Matsumae"),
            ),
        ),
    )
}

/** Context menu — CONTROLS: the repeat & shuffle state rows. */
@DevicePreviews
@Composable
internal fun NowPlayingControls(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    NowPlayingScreenshot(
        darkTheme,
        syntheticWidthClass,
        sampleModel(
            isPlaying = true,
            contextMenuMode = ContextMenuMode.CONTROLS,
            repeatMode = RepeatMode.ONE,
            isShuffled = true,
            repeatStatusLabel = "Repeating one track",
            shuffleStatusLabel = "Shuffling tracks",
        ),
    )
}

@Composable
private fun NowPlayingScreenshot(
    darkTheme: Boolean,
    syntheticWidthClass: WidthClass,
    model: NowPlayingModel,
) {
    ScreenPreview(
        darkTheme = darkTheme,
        syntheticWidthClass = syntheticWidthClass,
        screenName = "NowPlaying",
    ) {
        NowPlayingContent(model = model, actionSink = NoOpSink)
    }
}

private fun sampleModel(
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    errorMessage: String? = null,
    errors: List<NowPlayingError> = emptyList(),
    contextMenuMode: ContextMenuMode = ContextMenuMode.NONE,
    artists: List<NowPlayingArtist> = emptyList(),
    repeatMode: RepeatMode = RepeatMode.OFF,
    isShuffled: Boolean = false,
    repeatStatusLabel: String = "",
    shuffleStatusLabel: String = "",
): NowPlayingModel = NowPlayingModel(
    artwork = SourceInfo(info = "preview://mega-man-2"),
    sessionTypeLabel = "Playing from game",
    sessionSourceName = "Mega Man 2",
    title = "Dr. Wily Stage 1",
    artistsCaption = artists.joinToString(", ") { it.name }.ifEmpty { "Takashi Tateishi" },
    gameTitle = "Mega Man 2",
    isPlaying = isPlaying,
    isBuffering = isBuffering,
    positionMs = 72_000L,
    lengthMs = 154_000L,
    // Render-ahead has cached most of the track — the secondary fill runs ahead of the playhead.
    cachedMs = 120_000L,
    canSkipForward = true,
    isShuffled = isShuffled,
    repeatMode = repeatMode,
    errorMessage = errorMessage,
    errors = errors,
    contextMenuMode = contextMenuMode,
    gameId = 1L,
    artists = artists,
    repeatStatusLabel = repeatStatusLabel,
    shuffleStatusLabel = shuffleStatusLabel,
)

private const val SAMPLE_ERROR = "Couldn't load track"
