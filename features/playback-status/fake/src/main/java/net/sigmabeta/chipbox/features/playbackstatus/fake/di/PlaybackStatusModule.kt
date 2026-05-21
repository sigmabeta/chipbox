package net.sigmabeta.chipbox.features.playbackstatus.fake.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatusEntryPoint
import net.sigmabeta.chipbox.features.playbackstatus.fake.FakePlaybackStatusEntryPoint
import net.sigmabeta.sage.di.AppScope

@ContributesTo(AppScope::class)
interface PlaybackStatusModule {
    @Binds
    fun bindEntryPoint(impl: FakePlaybackStatusEntryPoint): PlaybackStatusEntryPoint
}
