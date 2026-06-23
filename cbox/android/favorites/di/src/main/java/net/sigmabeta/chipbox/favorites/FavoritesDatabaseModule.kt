package net.sigmabeta.chipbox.favorites

import android.content.Context
import androidx.room.Room
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object FavoritesDatabaseModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideFavoritesDatabase(context: Context): FavoritesDatabase = Room
        .databaseBuilder(
            context,
            FavoritesDatabase::class.java,
            "chipbox-favorites-database",
        )
        .fallbackToDestructiveMigration()
        .build()
}
