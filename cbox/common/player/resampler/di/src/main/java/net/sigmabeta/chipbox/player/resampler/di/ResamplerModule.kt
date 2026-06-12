package net.sigmabeta.chipbox.player.resampler.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.resampler.CubicResampler
import net.sigmabeta.chipbox.player.resampler.LinearResampler
import net.sigmabeta.chipbox.player.resampler.Resampler
import net.sigmabeta.chipbox.settings.ResamplerMode
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object ResamplerModule {
    /**
     * The single-technique resamplers keyed by the [ResamplerMode] that selects them. A sink injects
     * this map and picks the active resampler from the live setting (and bypasses for [ResamplerMode.OS],
     * which has no entry). Each value knows exactly one interpolation technique.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideResamplers(): Map<ResamplerMode, Resampler> = mapOf(
        ResamplerMode.LINEAR to LinearResampler(),
        ResamplerMode.CUBIC to CubicResampler(),
    )
}
