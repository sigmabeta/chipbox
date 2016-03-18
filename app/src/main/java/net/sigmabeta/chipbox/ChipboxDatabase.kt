package net.sigmabeta.chipbox

import com.raizlabs.android.dbflow.annotation.Database

@Database(name = ChipboxDatabase.NAME, version = ChipboxDatabase.VERSION)
class ChipboxDatabase {
    companion object {
        const val NAME = "chipbox"
        const val VERSION = 1
    }
}