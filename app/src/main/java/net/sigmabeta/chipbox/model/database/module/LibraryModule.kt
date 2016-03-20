package net.sigmabeta.chipbox.model.database.module

import android.content.Context
import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.model.database.Library
import net.sigmabeta.chipbox.util.logVerbose

@Module
class LibraryModule() {
    @Provides fun provideSongDatabaseHelper(context: Context): Library {
        logVerbose("[LibraryModule] Providing SongDatabaseHelper...")
        return Library(context)
    }
}

