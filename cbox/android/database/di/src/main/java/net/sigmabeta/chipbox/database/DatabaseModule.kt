package net.sigmabeta.chipbox.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import javax.inject.Singleton
import net.sigmabeta.sage.di.AppScope

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChipboxDatabase = Room
        .databaseBuilder(
            context,
            ChipboxDatabase::class.java,
            "chipbox-room-database"
        )
        .fallbackToDestructiveMigration()
        .build()
}
