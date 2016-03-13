package net.sigmabeta.chipbox.ui.file

import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.ui.BaseView
import java.util.*

interface FileListView : BaseView {
    fun updateSubtitle(path: String)

    fun showErrorMessage(errorId: Int)

    fun showExistsMessage()

    fun onAddSuccessful()

    fun setFiles(files: ArrayList<FileListItem>)
}
