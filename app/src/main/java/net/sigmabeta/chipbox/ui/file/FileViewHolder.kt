package net.sigmabeta.chipbox.ui.file

import android.view.View
import kotlinx.android.synthetic.main.list_item_file.view.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.util.TYPE_FOLDER
import net.sigmabeta.chipbox.util.TYPE_OTHER
import net.sigmabeta.chipbox.ui.BaseViewHolder
import net.sigmabeta.chipbox.ui.file.FileAdapter

public class FileViewHolder(view: View, adapter: FileAdapter) : BaseViewHolder<FileListItem, FileViewHolder, FileAdapter>(view, adapter), View.OnClickListener {
    var file: FileListItem? = null

    override fun getId(): Long? {
        return adapterPosition.toLong()
    }

    override fun bind(toBind: FileListItem) {
        file = toBind

        view.text_file_name.text = toBind.filename

        when (file?.type) {
            TYPE_FOLDER -> view.image_type.setImageResource(R.drawable.ic_folder)
            TYPE_OTHER -> view.image_type.setImageResource(R.drawable.ic_file_white_24dp)
            else -> view.image_type.setImageResource(R.drawable.ic_audiotrack_white_24dp)
        }
    }
}