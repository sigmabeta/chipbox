package net.sigmabeta.chipbox.player.persistence.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.persistence.PlaybackSessionPersister
import net.sigmabeta.chipbox.player.persistence.PlaybackSessionStore
import net.sigmabeta.chipbox.player.persistence.real.RealPlaybackSessionPersister
import net.sigmabeta.chipbox.player.persistence.real.RealPlaybackSessionStore
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.storage.common.Storage

@BindingContainer
@ContributesTo(AppScope::class)
object PlaybackSessionModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackSessionStore(storage: Storage): PlaybackSessionStore =
        RealPlaybackSessionStore(storage)

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackSessionPersister(
        director: Director,
        store: PlaybackSessionStore,
        hatchet: Hatchet,
    ): PlaybackSessionPersister = RealPlaybackSessionPersister(director, store, hatchet)
}
