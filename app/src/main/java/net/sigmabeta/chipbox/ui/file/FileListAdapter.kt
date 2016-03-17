package net.sigmabeta.chipbox.ui.file

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.ui.BaseArrayAdapter
import net.sigmabeta.chipbox.ui.ItemListView

public class FileListAdapter(view: ItemListView<FileViewHolder>) : BaseArrayAdapter<FileListItem, FileViewHolder>(view) {
    override fun getLayoutId(): Int {
        return R.layout.list_item_file
    }

    override fun createViewHolder(view: View): FileViewHolder {
        return FileViewHolder(view, this)
    }

    override fun bind(holder: FileViewHolder, item: FileListItem) {
        holder.bind(item)
    }
}