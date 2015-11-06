package net.sigmabeta.chipbox.view.interfaces

import android.database.Cursor

interface GameListView {
    fun onItemClick(id: Long)

    fun setCursor(cursor: Cursor)

}