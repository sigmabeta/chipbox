package net.sigmabeta.chipbox.view.interfaces

import android.database.Cursor

interface ArtistListView {
    fun onItemClick(id: Long, artistName: String)

    fun setCursor(cursor: Cursor)
}