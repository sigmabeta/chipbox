package net.sigmabeta.chipbox.view.interfaces

import net.sigmabeta.chipbox.model.file.FileListItem
import java.util.*

interface FileListView : BaseView {
    fun updateSubtitle(path: String)

    fun showErrorMessage(errorId: Int)

    fun showExistsMessage()

    fun onAddSuccessful()

    fun setFiles(files: ArrayList<FileListItem>)
}
