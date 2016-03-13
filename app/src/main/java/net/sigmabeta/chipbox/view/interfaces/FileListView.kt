package net.sigmabeta.chipbox.view.interfaces

interface FileListView : BaseView {
    fun onItemClick(path: String)

    fun updateSubtitle(path: String)

    fun upOneLevel()

    fun setPath(path: String)

    fun showErrorMessage(errorId: Int)

    fun showExistsMessage()

    fun onAddSuccessful()
}
