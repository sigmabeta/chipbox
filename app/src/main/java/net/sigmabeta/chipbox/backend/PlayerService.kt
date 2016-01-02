package net.sigmabeta.chipbox.backend

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.SystemClock
import net.sigmabeta.chipbox.dagger.injector.ServiceInjector
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.logDebug
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.BackendView
import javax.inject.Inject

class PlayerService : Service(), BackendView {
    var player: Player? = null
        @Inject set

    var notificationManager: MediaNotificationManager? = null

    var session: MediaSession? = null

    var noisyRegistered = false
    var noisyReceiver: NoisyReceiver? = null

    /**
     * Service
     */

    override fun onCreate() {
        super.onCreate()
        logVerbose("[PlayerService] Creating...")

        inject()

        noisyReceiver = NoisyReceiver(player!!)

        session = MediaSession(this, "Chipbox")

        session?.setCallback(SessionCallback(this))
        session?.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

        notificationManager = MediaNotificationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logVerbose("[PlayerService] Received StartCommand: ${intent?.action}")

        player?.backendView = this

        updatePlaybackState(null)
        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        logVerbose("[PlayerService] Destroying...")

        player?.backendView = null
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /**
     * BackendView
     */

    override fun play(track: Track) {
        logVerbose("[PlayerService] Processed PLAY command.")

        registerNoisyReceiver()

        updatePlaybackState(null)
    }

    override fun pause() {
        logVerbose("[PlayerService] Processed PAUSE command.")

        unregisterNoisyReceiver()

        updatePlaybackState(null)
    }

    override fun stop() {
        logVerbose("[PlayerService] Processed STOP command.")

        unregisterNoisyReceiver()

        updatePlaybackState(null)
        stopSelf()
    }

    override fun skipToNext() {
        logVerbose("[PlayerService] Processed NEXT command.")
        updatePlaybackState(null)
    }

    override fun skipToPrev() {
        logVerbose("[PlayerService] Processed PREV command.")
        updatePlaybackState(null)
    }

    /**
     * Public methods
     */

    fun getSessionToken(): MediaSession.Token? {
        return session?.sessionToken
    }

    /**
     * Private methods
     */

    private fun inject() {
        ServiceInjector.inject(this)
    }

    /**
     * Update the current media player state, optionally showing an error message.

     * @param error if not null, error message to present to the user.
     */
    private fun updatePlaybackState(error: String?) {
        logDebug("[PlayerService] Updating playback state: ${player?.state}")

        var position = player?.position ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN

        val stateBuilder = PlaybackState.Builder().setActions(getAvailableActions())

        var state = player?.state

        val track = player?.playingTrack
        val metadata = if (track != null) {
            Track.toMetadata(track)
        } else {
            null
        }

        session?.setMetadata(metadata)

        logVerbose("[PlayerService] Playing. \n" +
                "Track: ${track}\n" +
                "Metadata: ${metadata?.description}\n")

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(error)
            state = PlaybackState.STATE_ERROR
        }

        stateBuilder.setState(state ?: PlaybackState.STATE_ERROR, position, 1.0f, SystemClock.elapsedRealtime())

        session?.setPlaybackState(stateBuilder.build())

        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED) {
            notificationManager?.startNotification()
        }
    }

    private fun getAvailableActions(): Long {
        var actions = PlaybackState.ACTION_PLAY

        if (player?.state == PlaybackState.STATE_PLAYING) {
            actions = actions or PlaybackState.ACTION_PAUSE
        }

        val playbackQueuePosition = player?.playbackQueuePosition
        val playbackQueueSize = player?.playbackQueue?.size

        if (playbackQueuePosition != null) {
            if (playbackQueuePosition > 0) {
                actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
            }
            if (playbackQueueSize != null) {
                if (playbackQueuePosition < playbackQueueSize - 1) {
                    actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
                }
            }
        }

        return actions
    }

    private fun registerNoisyReceiver() {
        logVerbose("[Player] Registering NOISY BroadcastReceiver.")
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, intentFilter)

        noisyRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (noisyRegistered) {
            logVerbose("[Player] Unregistering NOISY BroadcastReceiver.")

            unregisterReceiver(noisyReceiver)
            noisyRegistered = false
        }
    }

    /**
     *
     */

    companion object {
        fun start(context: Context) {
            val launcher = Intent(context, PlayerService::class.java)

            context.startService(launcher)
        }
    }
}
