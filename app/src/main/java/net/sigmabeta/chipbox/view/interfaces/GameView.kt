package net.sigmabeta.chipbox.view.interfaces

import android.database.Cursor

interface GameView {
    fun setCursor(cursor: Cursor)

    fun getCursor(): Cursor?
}