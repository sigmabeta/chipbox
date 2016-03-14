package net.sigmabeta.chipbox.ui.file

import net.sigmabeta.chipbox.ui.BaseView

interface FilesView : BaseView {
    fun updateSubtitle(path: String)

    fun showErrorMessage(errorId: Int)

    fun showExistsMessage()

    fun onAddSuccessful()

    fun onDirectoryClicked(path: String)

    fun showFileFragment(path: String, stack: Boolean)

    fun popBackStack()
}
