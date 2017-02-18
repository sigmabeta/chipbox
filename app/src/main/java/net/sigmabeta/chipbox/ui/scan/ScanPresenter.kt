package net.sigmabeta.chipbox.ui.scan

import android.os.Bundle
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.model.events.*
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.util.logWarning
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ScanPresenter @Inject constructor(val updater: UiUpdater) : ActivityPresenter<ScanView>() {
    var filesAdded = 0
    var badFiles = 0

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun onTempDestroy() = Unit

    override fun setup(arguments: Bundle?) {
        needsSetup = false

        view?.startScanner()

        updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> { /* No-op*/
                        }
                        is PositionEvent -> { /* No-op*/
                        }
                        is GameEvent -> { /* No-op*/
                        }
                        is StateEvent -> { /* No-op*/
                        }
                        is FileScanEvent -> handleEvent(it)
                        is FileScanFailedEvent -> view?.onScanFailed()
                        is FileScanCompleteEvent -> view?.onScanComplete(true)
                        else -> logWarning("[PlayerFragmentPresenter] Unhandled ${it}")
                    }
                }
    }

    override fun teardown() {
        filesAdded = 0
        badFiles = 0
    }

    override fun updateViewState() = Unit

    override fun onClick(id: Int) = Unit

    private fun handleEvent(event: FileScanEvent) {
        var currentFolder: String? = null
        var lastFile: String? = null
        var lastError: String? = null

        when (event.type) {
            FileScanEvent.TYPE_FOLDER -> {
                currentFolder = event.name
            }

            FileScanEvent.TYPE_NEW_TRACK -> {
                filesAdded++
                lastFile = event.name
            }

            FileScanEvent.TYPE_BAD_TRACK -> {
                badFiles++
                lastError = event.name
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

    override fun onReenter() = Unit
}