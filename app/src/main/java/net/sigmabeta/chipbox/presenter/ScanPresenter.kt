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
                .buffer(17, TimeUnit.MILLISECONDS)
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

    private fun handleEvent(events: MutableList<FileScanEvent>) {
        var currentFolder: String? = null
        var lastFile: String? = null
        var lastError: String? = null

        for (event in events) {
            when (event.type) {
                FileScanEvent.TYPE_FOLDER -> {
                    currentFolder = event.name
                }

                FileScanEvent.TYPE_TRACK -> {
                    filesAdded++
                    lastFile = event.name
                }

                FileScanEvent.TYPE_BAD_TRACK -> {
                    badFiles++
                    lastError = event.name
                }
            }
        }

        if (currentFolder != null) {
            view.showCurrentFolder(currentFolder)
        }

        if (lastFile != null) {
            view.showLastFile(lastFile)
            view.updateFilesAdded(filesAdded)
        }

        if (lastError != null) {
            view.updateBadFiles(badFiles)
        }
    }
}