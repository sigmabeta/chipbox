package net.sigmabeta.chipbox.view.viewholder

import android.support.v7.widget.RecyclerView
import android.view.View
import kotlinx.android.synthetic.list_item_file.view.image_type
import kotlinx.android.synthetic.list_item_file.view.text_file_name
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.util.TYPE_FOLDER
import net.sigmabeta.chipbox.util.TYPE_OTHER
import net.sigmabeta.chipbox.view.adapter.FileAdapter

public class FileViewHolder(val view: View, val adapter: FileAdapter) : RecyclerView.ViewHolder(view), View.OnClickListener {
    var file: FileListItem? = null

    init {
        view.setOnClickListener(this)
    }

    fun bind(toBind: FileListItem) {
        file = toBind

        view.text_file_name.text = toBind.filename

        when (file?.type) {
            TYPE_FOLDER -> view.image_type.setImageResource(R.drawable.ic_folder)
            TYPE_OTHER -> view.image_type.setImageResource(R.drawable.ic_file_white_24dp)
            else -> view.image_type.setImageResource(R.drawable.ic_audiotrack_white_24dp)
        }
    }

    /**
     * When a file is clicked, determine if it is a directory; if it is, show that new directory's
     * contents. If it is not, end the activity successfully.

     * @param view The View representing the file the user clicked on.
     */
    override fun onClick(view: View) {
        val path = file?.path ?: return

        adapter.onItemClick(path)
    }
}