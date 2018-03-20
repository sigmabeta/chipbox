package net.sigmabeta.chipbox.backend

import android.app.IntentService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.support.v7.app.NotificationCompat
import android.util.Log
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.events.FileScanCompleteEvent
import net.sigmabeta.chipbox.model.events.FileScanEvent
import net.sigmabeta.chipbox.model.events.FileScanFailedEvent
import net.sigmabeta.chipbox.model.repository.LibraryScanner
import timber.log.Timber
import javax.inject.Inject


class ScanService : IntentService("Scanner") {
    lateinit var updater: UiUpdater
        @Inject set

    lateinit var scanner: LibraryScanner
        @Inject set

    val manager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onHandleIntent(intent: Intent?) {
        Timber.i("Scanning")

        manager.cancel(NOTIFICATION_ID_FAILED)

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
                            updater.send(FileScanFailedEvent(it))
                            Timber.e("File scanning error: %s", Log.getStackTraceString(it))
                        },
                        {
                            // OnCompleted.
                            updater.send(FileScanCompleteEvent(newTracks, updatedTracks))
                        }
                )
    }

    private fun showFailedNotification() {
        val builder = NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_pause)
                .setContentTitle(getString(R.string.notification_scan_failed_title))
                .setContentText(getString(R.string.notification_scan_failed_content))

        manager.notify(NOTIFICATION_ID_FAILED, builder.build())
    }

    private fun inject() {
        Timber.v("Injecting ScanService.")
        (application as ChipboxApplication).appComponent.inject(this)
    }

    companion object {
        val NOTIFICATION_ID_FAILED = 1234
    }
}
