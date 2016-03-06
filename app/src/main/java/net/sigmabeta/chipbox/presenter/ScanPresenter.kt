package net.sigmabeta.chipbox.presenter

import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.events.FileScanEvent
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.view.interfaces.ScanView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import javax.inject.Inject


@ActivityScoped
class ScanPresenter @Inject constructor(val view: ScanView,
                                        val database: SongDatabaseHelper ) {
    var filesAdded = 0
    var badFiles = 0

    fun onCreate() {
        database.scanLibrary()
                .throttleLast(32, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            // OnNext. it: FileScanEvent
                            handleEvent(it)
                        },
                        {
                            // OnError. it: Throwable
                            view.onScanFailed()
                            logError("[FileListPresenter] File scanning error: ${it.message}")
                        },
                        {
                            // OnCompleted.
                            view.onScanComplete()
                        }
                )
    }

    private fun handleEvent(event: FileScanEvent) {
        when (event.type) {
            FileScanEvent.TYPE_FOLDER -> {
                view.showCurrentFolder(event.name)
            }

            FileScanEvent.TYPE_TRACK -> {
                filesAdded++

                view.showLastFile(event.name)
                view.updateFilesAdded(filesAdded)
            }

            FileScanEvent.TYPE_BAD_TRACK -> {
                badFiles++

                view.updateBadFiles(badFiles)
            }
        }
    }
}