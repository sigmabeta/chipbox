package net.sigmabeta.chipbox.view.adapter

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.util.generateFileList
import net.sigmabeta.chipbox.view.interfaces.FileListView
import net.sigmabeta.chipbox.view.viewholder.FileViewHolder
import java.io.File
import java.util.*

public class FileAdapter(var currentPath: String, var fileList: ArrayList<FileListItem>, private val view: FileListView) : RecyclerView.Adapter<FileViewHolder>() {
    /**
     * Called by the LayoutManager when it is necessary to create a new view.
     *
     * @param parent   The RecyclerView (I think?) the created view will be thrown into.
     * @param viewType Not used here, but useful when more than one type of child will be used in the RecyclerView.
     * @return The created ViewHolder with references to all the child view's members.
     */
    override public fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        // Create a new view.
        val listItem = LayoutInflater.from(parent.context).inflate(R.layout.list_item_file, parent, false)

        // Use that view to create a ViewHolder.
        return FileViewHolder(listItem, this)
    }

    /**
     * Called by the LayoutManager when a new view is not necessary because we can recycle
     * an existing one (for example, if a view just scrolled onto the screen from the bottom, we
     * can use the view that just scrolled off the top instead of inflating a new one.)

     * @param holder   A ViewHolder representing the view we're recycling.
     * *
     * @param position The position of the 'new' view in the dataset.
     */
    override public fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        // Get a reference to the item from the dataset; we'll use this to fill in the view contents.
        val file = fileList.get(position)

        holder.bind(file)
    }

    /**
     * Called by the LayoutManager to find out how much data we have.

     * @return Size of the dataset.
     */
    override fun getItemCount(): Int {
        return fileList.size()
    }

    public fun setPath(path: String) {
        currentPath = path
        val directory = File(path)

        fileList = generateFileList(directory)
        notifyDataSetChanged()
        view.updateSubtitle(path)
    }

    public fun upOneLevel() {
        val currentDirectory = File(currentPath)
        val parentDirectory = currentDirectory.parentFile

        setPath(parentDirectory.absolutePath)
    }

    public fun onItemClick(path: String) {
        view.onItemClick(path)
    }
}