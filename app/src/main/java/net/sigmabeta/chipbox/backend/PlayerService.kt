package net.sigmabeta.chipbox.backend

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import net.sigmabeta.chipbox.dagger.injector.ServiceInjector
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.logDebug
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.activity.PlayerActivity
import net.sigmabeta.chipbox.view.interfaces.BackendView
import javax.inject.Inject

class PlayerService : Service(), BackendView {
    var player: Player? = null
        @Inject set

    var notificationManager: MediaNotificationManager? = null

    var session: MediaSessionCompat? = null

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

        session = MediaSessionCompat(this, "Chipbox")

        session?.setSessionActivity(getPlayerActivityIntent())

        session?.setCallback(SessionCallback(this))
        session?.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

        notificationManager = MediaNotificationManager(this)

        // A workaround for the fact that controllerCallback is null inside the init {} constructor.
        notificationManager?.setControllerCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logVerbose("[PlayerService] Received StartCommand: ${intent?.action} " +
                "-> ${intent?.extras?.get(Intent.EXTRA_KEY_EVENT)}")

        MediaButtonReceiver.handleIntent(session, intent)

        if (player?.backendView != this) {
            player?.backendView = this

            play()
        }

        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        logVerbose("[PlayerService] Destroying...")

        session?.release()

        player?.backendView = null
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /**
     * BackendView
     */

    override fun play() {
        logVerbose("[PlayerService] Processed PLAY command.")

        session?.isActive = true
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

        session?.isActive = false
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

    fun getSessionToken(): MediaSessionCompat.Token? {
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

        var state = player?.state

        val track = player?.playingTrack

        val metadata = if (track != null) {
            val imagesFolderPath = getExternalFilesDir(null).absolutePath + "/images/"
            val imagePath = imagesFolderPath + track.gameId.toString() + "/local.png"

            val metadataBuilder = Track.toMetadataBuilder(track)

            // TODO May need to do this asynchronously.
            val imageBitmap = BitmapFactory.decodeFile(imagePath)

            if (imageBitmap != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, imageBitmap)
            } else {
                logError("[PlayerService] Couldn't load game art.")
            }

            metadataBuilder.build()
        } else {
            null
        }

        session?.setMetadata(metadata)

        val stateBuilder = PlaybackStateCompat.Builder().setActions(getAvailableActions())

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(error)
            state = PlaybackState.STATE_ERROR
        }

        logVerbose("[PlayerService] Playback state: ${state} \n" +
                "Track: ${track}\n" +
                "Metadata: ${metadata?.description}\n")

        stateBuilder.setState(state ?: PlaybackState.STATE_ERROR, position, 1.0f, SystemClock.elapsedRealtime())

        session?.setPlaybackState(stateBuilder.build())

        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED
                && !(notificationManager?.notified ?: false)) {
            notificationManager?.startNotification()
        }
    }

    private fun getPlayerActivityIntent(): PendingIntent {
        return PendingIntent.getActivity(this, 0, PlayerActivity.getLauncher(this), 0)
    }

    private fun getAvailableActions(): Long {
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_STOP

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

        if (!noisyRegistered) {
            val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(noisyReceiver, intentFilter)

            noisyRegistered = true
        }
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
