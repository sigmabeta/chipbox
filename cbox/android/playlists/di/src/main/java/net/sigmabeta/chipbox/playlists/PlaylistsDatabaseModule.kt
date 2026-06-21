package net.sigmabeta.chipbox.playlists

import android.content.Context
import androidx.room.Room
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object PlaylistsDatabaseModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providePlaylistsDatabase(context: Context): PlaylistsDatabase = Room
        .databaseBuilder(
            context,
            PlaylistsDatabase::class.java,
            "chipbox-playlists-database",
        )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}
