package net.sigmabeta.chipbox.view.interfaces

import net.sigmabeta.chipbox.model.events.FileScanEvent

interface FileListView {
    fun onItemClick(path: String)

    fun updateSubtitle(path: String)

    fun upOneLevel()

    fun setPath(path: String)

    fun onAdditionComplete()

    fun onAdditionFailed()

    fun showToastMessage(message: String)

    fun showErrorMessage(errorId: Int)

    fun showProgressDialog()

    fun updateProgressText(event: FileScanEvent)
}
