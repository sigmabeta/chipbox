package net.sigmabeta.chipbox.backend

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ChipboxService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        throw UnsupportedOperationException()
    }
}
