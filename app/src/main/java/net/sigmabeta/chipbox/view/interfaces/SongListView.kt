package net.sigmabeta.chipbox.view.interfaces

import android.database.Cursor
import net.sigmabeta.chipbox.model.objects.Track

interface SongListView {
    fun onItemClick(track: Track, position: Int)

    fun setCursor(cursor: Cursor)

    fun getCursor(): Cursor?

    fun launchPlayerActivity()
}