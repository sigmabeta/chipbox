package net.sigmabeta.chipbox.backend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.sigmabeta.chipbox.backend.player.Player
import timber.log.Timber

class NoisyReceiver constructor(val player: Player): BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.w("Received BECOMING_NOISY intent.")
        player.pause()
    }
}