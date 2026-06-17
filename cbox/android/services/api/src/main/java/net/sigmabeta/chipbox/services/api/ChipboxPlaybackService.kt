package net.sigmabeta.chipbox.services.api

import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.history.PlaybackHistoryRecorder
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.persistence.PlaybackSessionPersister
import net.sigmabeta.sage.logging.Hatchet

class ChipboxPlaybackService : MediaLibraryService() {
    // Resolved in onCreate from `application as ChipboxServiceGraph`; assigned-once so the
    // accessors read like Hilt @Inject lateinit at the use sites but reach the Metro graph
    // instead. See docs/architecture/sage-integration.md (M6).
    private lateinit var libraryBrowser: LibraryBrowser
    private lateinit var director: Director
    private lateinit var hatchet: Hatchet
    private lateinit var sessionPersister: PlaybackSessionPersister
    private lateinit var historyRecorder: PlaybackHistoryRecorder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var session: MediaLibrarySession? = null
    private var directorPlayer: DirectorPlayer? = null
    private var noisyReceiver: BecomingNoisyReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val graph = application as? ChipboxServiceGraph
            ?: error(
                "Application ${application::class.java} does not implement ChipboxServiceGraph " +
                    "— the Metro graph cannot be reached from ChipboxPlaybackService.",
            )
        libraryBrowser = graph.libraryBrowser()
        director = graph.director()
        hatchet = graph.hatchet()
        sessionPersister = graph.playbackSessionPersister()
        historyRecorder = graph.playbackHistoryRecorder()

        hatchet.i("Starting service...")

        val player = DirectorPlayer(director, this, hatchet)
        val callback = ChipboxLibrarySessionCallback(libraryBrowser, serviceScope, hatchet)

        session = MediaLibrarySession.Builder(this, player, callback).build()
        directorPlayer = player

        val receiver = BecomingNoisyReceiver { player.pauseFromBecomingNoisy() }
        registerReceiver(receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        noisyReceiver = receiver

        // Bring back the last session (loaded but paused) and keep the saved snapshot in step
        // with playback for the rest of the service's life.
        sessionPersister.observe()
        serviceScope.launch { sessionPersister.restore() }

        // Record plays to the history database for the rest of the service's life.
        historyRecorder.observe()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away — the realistic "closed while still playing". Capture the position now
        // (a pause may never have happened) so the next launch resumes where we left off.
        sessionPersister.snapshotNow()
        val player = directorPlayer
        if (player == null || !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        hatchet.i("Destroying service...")
        sessionPersister.snapshotNow()
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
        const val ID_ROOT = "net.sigmabeta.chipbox.services.api" + ID_ROOT_INFIX

        const val ID_ROOT_FULL = ID_ROOT + "full"
        const val ID_ROOT_EMPTY = ID_ROOT + "empty"
    }
}
