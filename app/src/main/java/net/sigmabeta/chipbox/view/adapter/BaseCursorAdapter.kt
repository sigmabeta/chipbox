package net.sigmabeta.chipbox.view.adapter

import android.database.Cursor
import android.support.v7.widget.RecyclerView
import net.sigmabeta.chipbox.model.database.COLUMN_DB_ID
import net.sigmabeta.chipbox.util.logError

abstract class BaseCursorAdapter() : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var datasetValid = false
    var cursor: Cursor? = null

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (datasetValid) {
            val tempCursor = cursor

            if (tempCursor != null) {
                if (tempCursor.moveToPosition(position)) {
                    bind(holder, tempCursor)
                } else {
                    logError("[BaseCursorAdapter] Can't bind view; Cursor is not valid.")
                }
            }
        } else {
            logError("[BaseCursorAdapter] Can't bind view; dataset is not valid.")
        }
    }

    abstract protected fun bind(holder: RecyclerView.ViewHolder, cursor: Cursor)

    override fun setHasStableIds(hasStableIds: Boolean) {
        super.setHasStableIds(true)
    }

    override fun getItemCount(): Int {
        if (datasetValid) {
            return cursor?.count ?: 0
        }
        logError("[BaseCursorAdapter] Dataset is not valid.")
        return 0
    }

    override fun getItemId(position: Int): Long {
        val tempCursor = cursor

        if (datasetValid && tempCursor != null) {
            if (tempCursor.moveToPosition(position)) {
                return tempCursor.getLong(COLUMN_DB_ID)
            }
        }

        logError("[BaseCursorAdapter] Dataset is not valid.")
        return 0
    }

    fun changeCursor(cursor: Cursor) {
        swapCursor(cursor)?.close()
    }

    fun swapCursor(cursor: Cursor): Cursor? {
        val oldCursor = this.cursor

        this.cursor = cursor
        datasetValid = true

        notifyDataSetChanged()
        return oldCursor
    }
}
