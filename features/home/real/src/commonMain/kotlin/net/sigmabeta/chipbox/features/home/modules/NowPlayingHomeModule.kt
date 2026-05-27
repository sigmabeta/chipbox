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
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.StringProvider

/**
 * Top-of-Home row that surfaces a larger version of the bottom mini-player. The module hides
 * itself (emits [LCE.Uninitialized]) whenever the director has no current track or is in a
 * stopped/idle state, so the row only takes up screen space when there's something to play.
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

    override fun state(): Flow<LCE<HomeModuleSection>> = combine(
        director.metadataState(),
        director.playbackState(),
    ) { track, playback ->
        if (track == null || !playback.state.isLive()) {
            LCE.Uninitialized
        } else {
            LCE.Content(sectionFor(track, playback.state))
        }
    }

    private fun sectionFor(track: Track, state: PlayerState): HomeModuleSection {
        val card = NowPlayingHomeCardListModel(
            title = track.title,
            artistsCaption = track.artists?.joinToString(", ") { it.name }.orEmpty(),
            artwork = SourceInfo(info = track.game?.photoUrl),
            isPlaying = state.isPlayingLike(),
            isBuffering = state == PlayerState.BUFFERING,
            isError = state == PlayerState.ERROR,
            clickAction = HomeAction.NowPlayingCardClicked,
            playPauseAction = HomeAction.NowPlayingPlayPauseClicked,
        )
        return HomeModuleSection(
            title = stringProvider.getString(ChipboxStringId.HOME_SECTION_NOW_PLAYING),
            items = persistentListOf(card),
        )
    }

    private fun PlayerState.isLive(): Boolean = this != PlayerState.IDLE && this != PlayerState.STOPPED

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
