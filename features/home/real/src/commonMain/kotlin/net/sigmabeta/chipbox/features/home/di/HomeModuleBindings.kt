package net.sigmabeta.chipbox.features.home.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.modules.NowPlayingHomeModule
import net.sigmabeta.chipbox.features.home.modules.RandomGamesHomeModule
import net.sigmabeta.chipbox.features.home.modules.RngTakeTheWheelHomeModule
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
    fun bindRandomGamesModule(impl: RandomGamesHomeModule): HomeModule

    @Binds
    @IntoSet
    fun bindRngTakeTheWheelModule(impl: RngTakeTheWheelHomeModule): HomeModule
}
