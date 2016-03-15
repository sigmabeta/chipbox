package net.sigmabeta.chipbox.backend

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.MediaSession
import android.os.IBinder
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.widget.Toast
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.ui.player.PlayerActivity
import net.sigmabeta.chipbox.util.logVerbose
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

        notificationManager?.subscribeToUpdates()

        notificationManager?.startNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logVerbose("[PlayerService] Received StartCommand: ${intent?.action} " +
                "-> ${intent?.extras?.get(Intent.EXTRA_KEY_EVENT)}")

        MediaButtonReceiver.handleIntent(session, intent)

        if (player?.backendView != this) {
            player?.backendView = this

            play()
        }

        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        logVerbose("[PlayerService] Destroying...")

        session?.release()

        player?.backendView = null

        notificationManager?.unsubscribeFromUpdates()
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
    }

    override fun pause() {
        logVerbose("[PlayerService] Processed PAUSE command.")

        unregisterNoisyReceiver()
    }

    override fun stop() {
        logVerbose("[PlayerService] Processed STOP command.")

        session?.isActive = false
        unregisterNoisyReceiver()

        stopSelf()
    }

    override fun skipToNext() {
        logVerbose("[PlayerService] Processed NEXT command.")
    }

    override fun skipToPrev() {
        logVerbose("[PlayerService] Processed PREV command.")
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
        logVerbose("[ServiceInjector] Injecting BackendView.")
        ChipboxApplication.appComponent.inject(this)
    }

    private fun getPlayerActivityIntent(): PendingIntent {
        return PendingIntent.getActivity(this, 0, PlayerActivity.getLauncher(this), 0)
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
