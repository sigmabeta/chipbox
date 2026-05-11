package net.sigmabeta.chipbox.services

import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Inject

@AndroidEntryPoint
class ChipboxPlaybackService : MediaLibraryService() {
    @Inject
    lateinit var libraryBrowser: LibraryBrowser

    @Inject
    lateinit var director: Director

    @Inject
    lateinit var hatchet: Hatchet

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var session: MediaLibrarySession? = null
    private var directorPlayer: DirectorPlayer? = null
    private var noisyReceiver: BecomingNoisyReceiver? = null

    override fun onCreate() {
        super.onCreate()
        hatchet.i("Starting service...")

        val player = DirectorPlayer(director, this, hatchet)
        val callback = ChipboxLibrarySessionCallback(libraryBrowser, serviceScope, hatchet)

        session = MediaLibrarySession.Builder(this, player, callback).build()
        directorPlayer = player

        val receiver = BecomingNoisyReceiver { player.pauseFromBecomingNoisy() }
        registerReceiver(receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        noisyReceiver = receiver
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return session
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = directorPlayer
        if (player == null || !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        hatchet.i("Destroying service...")
        noisyReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (t: Throwable) {
                hatchet.w("Failed to unregister noisy receiver: $t")
            }
        }
        noisyReceiver = null
        session?.release()
        session = null
        directorPlayer?.release()
        directorPlayer = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ID_ROOT_INFIX = ".media."
        const val ID_ROOT = "net.sigmabeta.chipbox.services" + ID_ROOT_INFIX

        const val ID_ROOT_FULL = ID_ROOT + "full"
        const val ID_ROOT_EMPTY = ID_ROOT + "empty"
    }
}
