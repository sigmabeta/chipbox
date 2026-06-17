package net.sigmabeta.chipbox.history

import android.content.Context
import androidx.room.Room
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object HistoryDatabaseModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryDatabase(context: Context): HistoryDatabase = Room
        .databaseBuilder(
            context,
            HistoryDatabase::class.java,
            "chipbox-history-database",
        )
        .fallbackToDestructiveMigration()
        .build()
}
