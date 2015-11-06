package net.sigmabeta.chipbox.model.database.module

import android.content.Context
import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.util.logVerbose

@Module
class DatabaseModule() {
    @Provides fun provideSongDatabaseHelper(context: Context): SongDatabaseHelper {
        logVerbose("[DatabaseModule] Providing SongDatabaseHelper...")
        return SongDatabaseHelper(context)
    }
}

