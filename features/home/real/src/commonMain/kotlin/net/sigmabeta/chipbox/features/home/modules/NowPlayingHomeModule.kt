package net.sigmabeta.chipbox.features.home.modules

import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import net.sigmabeta.chipbox.common.ui.components.api.NowPlayingHomeCardListModel
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.StringProvider

/**
 * Top-of-Home row that surfaces a larger version of the bottom mini-player. The module hides
 * itself (emits [LCE.Uninitialized]) whenever the director has no current track — i.e. no
 * metadata to show — so the row only takes up screen space when there's a track to surface.
 * A stopped/idle track still has a title/artist, so playback state no longer gates the row.
 *
 * The section's `items` always has exactly one element, which means
 * [net.sigmabeta.chipbox.features.home.HomeState] renders it full-width (per the single-item
 * special case) rather than as a one-card horizontal scroller.
 */
class NowPlayingHomeModule @Inject constructor(
    private val director: Director,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    // The card itself shows the track title prominently, so a separate "Now playing" header
    // would just duplicate it. Suppress it.
    override val showHeader = false

    override fun state(): Flow<LCE<HomeModuleSection>> = combine(
        director.metadataState(),
        director.playbackState(),
    ) { track, playback ->
        if (track == null) {
            LCE.Uninitialized
        } else {
            LCE.Content(sectionFor(track, playback))
        }
    }

    private fun sectionFor(track: Track, playback: ChipboxPlaybackState): HomeModuleSection {
        val card = NowPlayingHomeCardListModel(
            title = track.title,
            artistsCaption = track.artists?.joinToString(", ") { it.name }.orEmpty(),
            artwork = SourceInfo(info = track.game?.photoUrl),
            isPlaying = playback.state.isPlayingLike(),
            isBuffering = playback.state == PlayerState.BUFFERING,
            isError = playback.state == PlayerState.ERROR,
            progressFraction = progressFractionOf(playback.position, track.trackLengthMs),
            clickAction = HomeAction.NowPlayingCardClicked,
            playPauseAction = HomeAction.NowPlayingPlayPauseClicked,
            appearAction = HomeAction.NowPlayingCardAppeared,
            disappearAction = HomeAction.NowPlayingCardDisappeared,
        )
        return HomeModuleSection(
            title = stringProvider.getString(ChipboxStringId.HOME_SECTION_NOW_PLAYING),
            items = persistentListOf(card),
        )
    }

    // 0..1, with 0 for "unknown length" so the indicator sits empty rather than full or NaN.
    private fun progressFractionOf(positionMs: Long, trackLengthMs: Long): Float =
        if (trackLengthMs <= 0L) {
            0f
        } else {
            (positionMs.toFloat() / trackLengthMs.toFloat()).coerceIn(0f, 1f)
        }

    // Mirrors PlayerStatusViewModel.isPlaying — BUFFERING/ENDING count as "playing" so the
    // pause icon stays visible across short transitions instead of flipping to play and back.
    private fun PlayerState.isPlayingLike(): Boolean = when (this) {
        PlayerState.PLAYING, PlayerState.BUFFERING, PlayerState.ENDING -> true
        PlayerState.PAUSED, PlayerState.ERROR, PlayerState.IDLE, PlayerState.STOPPED -> false
    }

    private companion object {
        const val ID = "now_playing"

        // Top of screen — leave headroom below it for everything else.
        const val PRIORITY = 0
    }
}
