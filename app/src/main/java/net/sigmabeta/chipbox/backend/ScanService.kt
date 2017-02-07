package net.sigmabeta.chipbox.backend

import android.app.IntentService
import android.content.Intent
import android.util.Log
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.model.events.FileScanCompleteEvent
import net.sigmabeta.chipbox.model.events.FileScanFailedEvent
import net.sigmabeta.chipbox.model.repository.LibraryScanner
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import javax.inject.Inject

class ScanService : IntentService("Scanner") {
    lateinit var updater: UiUpdater
        @Inject set

    lateinit var scanner: LibraryScanner
        @Inject set

    override fun onHandleIntent(intent: Intent?) {
        logInfo("Scanning")

        inject()

        scanner.scanLibrary()
                .subscribe(
                        {
                            val lastEvent = it
                            updater.send(lastEvent)
                        },
                        {
                            // OnError. it: Throwable
                            showFailedNotification()
                            updater.send(FileScanFailedEvent(it.message ?: "Unknown error."))
                            logError("[ScanService] File scanning error: ${Log.getStackTraceString(it)}")
                        },
                        {
                            // OnCompleted.
                            updater.send(FileScanCompleteEvent(12, 24))
                        }
                )
    }

    private fun showFailedNotification() {

    }

    private fun inject() {
        logVerbose("[ServiceInjector] Injecting BackendView.")
        (application as ChipboxApplication).appComponent.inject(this)
    }
}
