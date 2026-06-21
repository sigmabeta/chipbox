package net.sigmabeta.chipbox.favorites.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.favorites.FavoritesDatabase
import net.sigmabeta.chipbox.favorites.FavoritesRepository
import net.sigmabeta.chipbox.favorites.real.RealFavoritesRepository
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@BindingContainer
@ContributesTo(AppScope::class)
object FavoritesModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideFavoritesRepository(
        database: FavoritesDatabase,
        hatchet: Hatchet,
    ): FavoritesRepository = RealFavoritesRepository(
        database.trackFavoriteDao(),
        database.gameFavoriteDao(),
        database.artistFavoriteDao(),
        hatchet,
    )
}
