package net.sigmabeta.chipbox.view.adapter

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.view.interfaces.ItemListView
import net.sigmabeta.chipbox.view.viewholder.FileViewHolder

public class FileAdapter(view: ItemListView) : BaseArrayAdapter<FileListItem, FileViewHolder>(view) {
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