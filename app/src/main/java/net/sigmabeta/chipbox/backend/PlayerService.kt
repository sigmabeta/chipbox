package net.sigmabeta.chipbox.backend

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.MediaSession
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_STOP
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.backend.player.Playlist
import net.sigmabeta.chipbox.model.repository.Repository
import net.sigmabeta.chipbox.ui.player.PlayerActivity
import timber.log.Timber
import javax.inject.Inject

class PlayerService : Service(), BackendView {
    lateinit var player: Player
        @Inject set

    lateinit var playlist: Playlist
        @Inject set

    lateinit var repository: Repository
        @Inject set

    lateinit var updater: UiUpdater
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
        Timber.v("Creating...")

        inject()

        noisyReceiver = NoisyReceiver(player)

        session = MediaSessionCompat(this, "Chipbox")

        session?.setSessionActivity(getPlayerActivityIntent())

        session?.setCallback(SessionCallback(this))
        session?.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

        repository.reopen()
        notificationManager = MediaNotificationManager(this, repository, player, playlist, updater)
        notificationManager?.subscribeToUpdates()
        notificationManager?.startNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.v("Received StartCommand: %s -> %s", intent?.action, intent?.extras?.get(Intent.EXTRA_KEY_EVENT))

        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent = intent.extras?.get(Intent.EXTRA_KEY_EVENT) as KeyEvent?
            if (keyEvent?.keyCode == KEYCODE_MEDIA_STOP) {
                Timber.w("Notification was swiped away.")
                notificationManager?.startNotification(true)
                notificationManager?.stopNotification()
            }
        }

        MediaButtonReceiver.handleIntent(session, intent)

        if (player?.backendView != this) {
            player?.backendView = this

            play()
        }

        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.v("Destroying...")

        session?.release()

        player?.backendView = null

        notificationManager?.unsubscribeFromUpdates()
        repository.close()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /**
     * BackendView
     */

    override fun play() {
        Timber.v("Processed PLAY command.")

        session?.isActive = true
        notificationManager?.startNotification(true)
        registerNoisyReceiver()
    }

    override fun pause() {
        Timber.v("Processed PAUSE command.")

        unregisterNoisyReceiver()
    }

    override fun stop() {
        Timber.v("Processed STOP command.")

        session?.isActive = false
        unregisterNoisyReceiver()
        notificationManager?.stopNotification()

        stopSelf()
    }

    override fun skipToNext() {
        Timber.v("Processed NEXT command.")
    }

    override fun skipToPrev() {
        Timber.v("Processed PREV command.")
    }

    override fun onGameLoadError() {
        Toast.makeText(this, "Couldn't load game.", Toast.LENGTH_SHORT).show()
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
        Timber.v("Injecting BackendView.")
        (application as ChipboxApplication).appComponent.inject(this)
    }

    private fun getPlayerActivityIntent(): PendingIntent {
        return PendingIntent.getActivity(this, 0, PlayerActivity.getLauncher(this), 0)
    }

    private fun registerNoisyReceiver() {
        Timber.v("Registering NOISY BroadcastReceiver.")

        if (!noisyRegistered) {
            val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(noisyReceiver, intentFilter)

            noisyRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (noisyRegistered) {
            Timber.v("Unregistering NOISY BroadcastReceiver.")

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

            ContextCompat.startForegroundService(context, launcher)
        }
    }
}
