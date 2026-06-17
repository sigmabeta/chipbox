package net.sigmabeta.chipbox.player.director.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.real.RealDirector
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@BindingContainer
@ContributesTo(AppScope::class)
object DirectorModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideDirector(
        generator: Generator,
        speaker: Speaker,
        repository: Repository,
        settingsManager: ChipboxSettingsManager,
        hatchet: Hatchet,
    ): Director = RealDirector(generator, speaker, repository, settingsManager, hatchet)
}
