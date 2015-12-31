package net.sigmabeta.chipbox.backend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.sigmabeta.chipbox.util.logWarning

class NoisyReceiver constructor(val player: Player): BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        logWarning("[NoisyReceiver] Received BECOMING_NOISY intent.")
        player.pause()
    }
}