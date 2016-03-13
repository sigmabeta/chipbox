package net.sigmabeta.chipbox.view.adapter

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.util.generateFileList
import net.sigmabeta.chipbox.view.interfaces.FileListView
import net.sigmabeta.chipbox.view.interfaces.ItemListView
import net.sigmabeta.chipbox.view.viewholder.FileViewHolder
import java.io.File

public class FileAdapter(view: ItemListView) : BaseArrayAdapter<FileListItem, FileViewHolder>(view) {
    var currentPath: String? = null

    override fun getLayoutId(): Int {
        return R.layout.list_item_file
    }

    override fun createViewHolder(view: View): FileViewHolder {
        return FileViewHolder(view, this)
    }

    override fun bind(holder: FileViewHolder, item: FileListItem) {
        holder.bind(item)
    }

    // TODO Everything below here MUST GO
    // TODO ...to a FileListPresenter

    public fun setPath(path: String) {
        currentPath = path
        val directory = File(path)

        dataset = generateFileList(directory)
        notifyDataSetChanged()

        if (view is FileListView) {
            view.updateSubtitle(path)
        }
    }

    fun upOneLevel() {
        val currentDirectory = File(currentPath)
        val parentDirectory = currentDirectory.parentFile

        setPath(parentDirectory.absolutePath)
    }

    fun onItemClick(path: String) {
        if (view is FileListView) {
            view.onItemClick(path)
        }
    }
}