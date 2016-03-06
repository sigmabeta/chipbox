package net.sigmabeta.chipbox.view.interfaces

interface FileListView {
    fun onItemClick(path: String)

    fun updateSubtitle(path: String)

    fun upOneLevel()

    fun setPath(path: String)

    fun showToastMessage(message: String)

    fun showErrorMessage(errorId: Int)

    fun startScanActivity()

    fun showExistsMessage()
}
