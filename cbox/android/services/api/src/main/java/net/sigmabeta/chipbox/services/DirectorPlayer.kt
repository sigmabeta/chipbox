package net.sigmabeta.chipbox.services

import android.content.Context
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.services.transformers.toMediaMetadata
import net.sigmabeta.sage.logging.Hatchet

class DirectorPlayer(
    private val director: Director,
    context: Context,
    private val hatchet: Hatchet,
) : SimpleBasePlayer(Looper.getMainLooper()), AudioFocusHelper.Callbacks {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioFocusHelper = AudioFocusHelper(context, this)

    private var currentTrack: Track? = null
    private var playbackState: ChipboxPlaybackState = INITIAL_STATE
    private var requestedPlayWhenReady: Boolean = false
    private var shuffled: Boolean = false

    init {
        director.metadataState()
            .onEach { track ->
                currentTrack = track
                invalidateState()
            }
            .launchIn(scope)

        director.playbackState()
            .onEach { state ->
                playbackState = state
                requestedPlayWhenReady = when (state.state) {
                    PlayerState.PLAYING,
                    PlayerState.BUFFERING,
                    PlayerState.PRELOADING,
                    PlayerState.FAST_FORWARDING,
                    PlayerState.REWINDING,
                    PlayerState.ENDING -> true
                    PlayerState.PAUSED,
                    PlayerState.STOPPED,
                    PlayerState.IDLE,
                    PlayerState.ERROR -> false
                }
                invalidateState()
            }
            .launchIn(scope)

        director.sessionState()
            .onEach { session ->
                shuffled = session?.shuffled == true
                invalidateState()
            }
            .launchIn(scope)
    }

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlayWhenReady(
                requestedPlayWhenReady,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setPlaybackState(playbackState.state.toMedia3PlaybackState())
            .setPlaybackParameters(PlaybackParameters(playbackState.playbackSpeed))
            .setContentPositionMs(playbackState.position)
            .setContentBufferedPositionMs { playbackState.generatorProducedMs }
            .setShuffleModeEnabled(shuffled)
            .setAudioAttributes(AUDIO_ATTRIBUTES)

        if (playbackState.state == PlayerState.ERROR) {
            builder.setPlayerError(
                PlaybackException(
                    playbackState.errorMessage,
                    null,
                    PlaybackException.ERROR_CODE_UNSPECIFIED,
                )
            )
        }

        val track = currentTrack
        if (track != null) {
            // Placeholder slots make seekToNext/Previous reachable — SimpleBasePlayer gates
            // them on the timeline having an adjacent window. They mirror the current track's
            // metadata so media3's optimistic index update doesn't flash empty UI mid-transition.
            val items = buildList {
                add(placeholderItem(PREV_PLACEHOLDER_UID, track))
                add(currentItem(track))
                if (playbackState.skipForwardAllowed) {
                    add(placeholderItem(NEXT_PLACEHOLDER_UID, track))
                }
            }
            builder.setPlaylist(items)
            builder.setCurrentMediaItemIndex(1)
        }

        return builder.build()
    }

    private fun currentItem(track: Track): MediaItemData {
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setMediaMetadata(track.toMediaMetadata())
            .build()
        return MediaItemData.Builder(track.id)
            .setMediaItem(mediaItem)
            .setDurationUs(track.trackLengthMs * MICROS_PER_MILLI)
            .setIsSeekable(true)
            .setIsDynamic(false)
            .build()
    }

    private fun placeholderItem(uid: String, mirror: Track): MediaItemData {
        val mediaItem = MediaItem.Builder()
            .setMediaId(uid)
            .setMediaMetadata(mirror.toMediaMetadata())
            .build()
        return MediaItemData.Builder(uid)
            .setMediaItem(mediaItem)
            .setDurationUs(mirror.trackLengthMs * MICROS_PER_MILLI)
            .setIsSeekable(false)
            .setIsDynamic(false)
            .setIsPlaceholder(true)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        requestedPlayWhenReady = playWhenReady
        if (playWhenReady) {
            if (audioFocusHelper.requestFocus()) {
                director.play()
            } else {
                hatchet.e("Audio focus denied.")
                requestedPlayWhenReady = false
            }
        } else {
            director.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        director.setShuffled(shuffleModeEnabled)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        requestedPlayWhenReady = false
        director.stop()
        audioFocusHelper.abandonFocus()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        director.stop()
        audioFocusHelper.abandonFocus()
        scope.cancel()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> director.skipForward()
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> director.skipBack()
            else -> {
                // Update synchronously so the next getState() reflects the seek target. Without
                // this, media3 records the pre-seek position with a fresh timestamp and extrapolates
                // forward from it, making the notification clock drift until Director's flow catches up.
                playbackState = playbackState.copy(position = positionMs)
                director.seek(positionMs)
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        val resolvedStart = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        val target = mediaItems.getOrNull(resolvedStart) ?: return Futures.immediateVoidFuture()
        try {
            IdToCommandParser.handleCommand(director, target.mediaId, hatchet)
        } catch (t: Throwable) {
            hatchet.e("Failed to parse media id ${target.mediaId}: $t")
            return Futures.immediateVoidFuture()
        }
        if (audioFocusHelper.requestFocus()) {
            requestedPlayWhenReady = true
            director.play()
        }
        return Futures.immediateVoidFuture()
    }

    fun pauseFromBecomingNoisy() {
        if (requestedPlayWhenReady) {
            director.pause()
        }
    }

    override fun onFocusLoss() {
        director.pause()
    }

    override fun onFocusLossTransient() {
        director.pauseTemporarily()
    }

    override fun onFocusLossTransientCanDuck() {
        director.duck()
    }

    override fun onFocusGain() {
        director.resumeFocus()
    }

    private fun PlayerState.toMedia3PlaybackState(): Int = when (this) {
        PlayerState.IDLE,
        PlayerState.STOPPED,
        PlayerState.ERROR -> Player.STATE_IDLE
        PlayerState.BUFFERING -> Player.STATE_BUFFERING
        PlayerState.PRELOADING,
        PlayerState.PLAYING,
        PlayerState.FAST_FORWARDING,
        PlayerState.REWINDING,
        PlayerState.PAUSED,
        PlayerState.ENDING -> Player.STATE_READY
    }

    companion object {
        private const val MICROS_PER_MILLI = 1_000L

        private val INITIAL_STATE = ChipboxPlaybackState(
            state = PlayerState.IDLE,
            position = 0L,
            generatorProducedMs = 0L,
            playbackSpeed = 1.0f,
            skipForwardAllowed = false,
            errorMessage = null,
        )

        private const val PREV_PLACEHOLDER_UID = "chipbox.prev_placeholder"
        private const val NEXT_PLACEHOLDER_UID = "chipbox.next_placeholder"

        // Only the *_MEDIA_ITEM variants so BasePlayer.seekToPrevious doesn't apply its own
        // "seek to 0 if past threshold" logic — Director.skipBack already owns that decision.
        private val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_RELEASE,
            )
            .build()

        private val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }
}
