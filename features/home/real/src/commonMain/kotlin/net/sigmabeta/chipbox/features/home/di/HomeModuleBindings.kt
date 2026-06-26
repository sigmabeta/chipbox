package net.sigmabeta.chipbox.features.home.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.modules.GameOfTheDayHomeModule
import net.sigmabeta.chipbox.features.home.modules.MostPlayedArtistsHomeModule
import net.sigmabeta.chipbox.features.home.modules.MostPlayedGamesHomeModule
import net.sigmabeta.chipbox.features.home.modules.MostPlayedSongsHomeModule
import net.sigmabeta.chipbox.features.home.modules.NowPlayingHomeModule
import net.sigmabeta.chipbox.features.home.modules.RecentlyAddedGamesHomeModule
import net.sigmabeta.chipbox.features.home.modules.RecentlyPlayedGamesHomeModule
import net.sigmabeta.chipbox.features.home.modules.RngTakeTheWheelHomeModule
import net.sigmabeta.chipbox.features.home.modules.ScanStatusHomeModule
import net.sigmabeta.sage.di.AppScope

@ContributesTo(AppScope::class)
interface HomeModuleBindings {

    @Multibinds
    fun homeModules(): Set<HomeModule>

    @Binds
    @IntoSet
    fun bindNowPlayingModule(impl: NowPlayingHomeModule): HomeModule

    @Binds
    @IntoSet
    fun bindGameOfTheDayModule(impl: GameOfTheDayHomeModule): HomeModule

    @Binds
    @IntoSet
    fun bindRecentlyAddedGamesModule(impl: RecentlyAddedGamesHomeModule): HomeModule

    @Binds
    @IntoSet
    fun bindRecentlyPlayedGamesModule(impl: RecentlyPlayedGamesHomeModule): HomeModule

    @Binds
    @IntoSet
    fun bindMostPlayedSongsModule(impl: MostPlayedSongsHomeModule): HomeModule

    @Binds
    @IntoSet
    fun bindMostPlayedGamesModule(impl: MostPlayedGamesHomeModule): HomeModule

    @Binds
    @IntoSet
    fun bindMostPlayedArtistsModule(impl: MostPlayedArtistsHomeModule): HomeModule

    @Binds
    @IntoSet
    fun bindRngTakeTheWheelModule(impl: RngTakeTheWheelHomeModule): HomeModule

    @Binds
    @IntoSet
    fun bindScanStatusModule(impl: ScanStatusHomeModule): HomeModule
}
