package net.sigmabeta.chipbox.database

import android.content.Context
import androidx.room.Room
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object DatabaseModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(context: Context): ChipboxDatabase = Room
        .databaseBuilder(
            context,
            ChipboxDatabase::class.java,
            "chipbox-room-database"
        )
        .fallbackToDestructiveMigration()
        .build()
}
