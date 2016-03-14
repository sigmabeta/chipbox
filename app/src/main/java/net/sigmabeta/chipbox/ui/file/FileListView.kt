package net.sigmabeta.chipbox.ui.file

import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.ui.BaseView
import java.util.*

interface FileListView : BaseView {
    fun setFiles(files: ArrayList<FileListItem>)

    fun onDirectoryClicked(path: String)

    fun onInvalidTrackClicked()
}