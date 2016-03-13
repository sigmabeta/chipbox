package net.sigmabeta.chipbox.ui.scan

import android.os.Bundle
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.events.FileScanEvent
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.scan.ScanView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ScanPresenter @Inject constructor(val database: SongDatabaseHelper) : ActivityPresenter() {
    var view: ScanView? = null

    var filesAdded = 0
    var badFiles = 0

    var backAllowed = false

    fun onBackPressed() {
        if (backAllowed) {
            view?.finish()
        }
    }

    override fun onReCreate(savedInstanceState: Bundle) {
    }

    override fun onTempDestroy() {
    }

    override fun setup(arguments: Bundle?) {
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
                            view?.onScanFailed()
                            backAllowed = true
                            logError("[FileListPresenter] File scanning error: ${it.message}")
                        },
                        {
                            // OnCompleted.
                            backAllowed = true
                            view?.onScanComplete()
                        }
                )
    }

    override fun teardown() {
        filesAdded = 0
        badFiles = 0
        backAllowed = false
    }

    override fun updateViewState() {
    }

    override fun setView(view: BaseView) {
        if (view is ScanView) this.view = view
    }

    override fun clearView() {
        view = null
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
            view?.showCurrentFolder(currentFolder)
        }

        if (lastFile != null) {
            view?.showLastFile(lastFile)
            view?.updateFilesAdded(filesAdded)
        }

        if (lastError != null) {
            view?.updateBadFiles(badFiles)
        }
    }
}