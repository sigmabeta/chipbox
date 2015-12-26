package net.sigmabeta.chipbox.view.interfaces

import android.database.Cursor

interface SongListView {
    fun onItemClick(id: Long, position: Int)

    fun setCursor(cursor: Cursor)

    fun getCursor(): Cursor?

    fun launchPlayerActivity(id: Long)
}