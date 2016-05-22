package net.sigmabeta.chipbox.ui.scan

import net.sigmabeta.chipbox.ui.BaseView

interface ScanView : BaseView {
    fun onScanFailed()

    fun onScanComplete(refresh: Boolean)

    fun showCurrentFolder(name: String)

    fun showLastFile(name: String)

    fun updateFilesAdded(filesAdded: Int)

    fun updateBadFiles(badFiles: Int)

    fun finish()
}