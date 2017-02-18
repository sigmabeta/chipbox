package net.sigmabeta.chipbox.backend

import android.app.IntentService
import android.content.Intent
import android.util.Log
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.model.events.FileScanCompleteEvent
import net.sigmabeta.chipbox.model.events.FileScanEvent
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

        var newTracks = 0
        var updatedTracks = 0

        scanner.scanLibrary()
                .subscribe(
                        {
                            updater.send(it)

                            when (it.type) {
                                FileScanEvent.TYPE_NEW_TRACK -> newTracks++
                                FileScanEvent.TYPE_NEW_MULTI_TRACK -> newTracks += it.count
                                FileScanEvent.TYPE_UPDATED_TRACK -> updatedTracks++
                            }
                        },
                        {
                            // OnError. it: Throwable
                            showFailedNotification()
                            updater.send(FileScanFailedEvent(it.message ?: "Unknown error."))
                            logError("[ScanService] File scanning error: ${Log.getStackTraceString(it)}")
                        },
                        {
                            // OnCompleted.
                            updater.send(FileScanCompleteEvent(newTracks, updatedTracks))
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
