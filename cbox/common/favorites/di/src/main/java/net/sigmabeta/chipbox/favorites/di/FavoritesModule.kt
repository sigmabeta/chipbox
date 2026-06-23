package net.sigmabeta.chipbox.favorites.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.FavoritesSource
import net.sigmabeta.chipbox.favorites.FavoritesDatabase
import net.sigmabeta.chipbox.favorites.FavoritesRepository
import net.sigmabeta.chipbox.favorites.fake.FakeFavoritesRepository
import net.sigmabeta.chipbox.favorites.real.RealFavoritesRepository
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@BindingContainer
@ContributesTo(AppScope::class)
object FavoritesModule {
    // The debug menu can swap the Room-backed store for an in-memory fake. Read once here at graph
    // build (so it applies on the next launch), mirroring the repository/generator/speaker switches.
    @Provides
    @SingleIn(AppScope::class)
    fun provideFavoritesRepository(
        database: FavoritesDatabase,
        debugSettingsManager: DebugSettingsManager,
        hatchet: Hatchet,
    ): FavoritesRepository = when (runBlocking { debugSettingsManager.getFavoritesSource().first() }) {
        FavoritesSource.REAL -> RealFavoritesRepository(
            database.trackFavoriteDao(),
            database.gameFavoriteDao(),
            database.artistFavoriteDao(),
            hatchet,
        )

        FavoritesSource.FAKE -> FakeFavoritesRepository()
    }
}
