package net.sigmabeta.chipbox.backend

import android.support.v4.media.session.MediaSessionCompat
import net.sigmabeta.chipbox.util.logVerbose

class SessionCallback(val playerService: PlayerService) : MediaSessionCompat.Callback() {
    override fun onPlay() {
        logVerbose("[SessionCallback] Received PLAY command.")

        playerService.player?.start(null)
    }

    override fun onStop() {
        logVerbose("[SessionCallback] Received STOP command.")

        playerService.player?.stop()
    }

    override fun onPause() {
        logVerbose("[SessionCallback] Received PAUSE command.")

        playerService.player?.pause()
    }

    override fun onSkipToNext() {
        logVerbose("[SessionCallback] Received NEXT command.")

        playerService.player?.skipToNext()
    }

    override fun onSkipToPrevious() {
        logVerbose("[SessionCallback] Received PREV command.")

        playerService.player?.skipToPrev()
    }
}
