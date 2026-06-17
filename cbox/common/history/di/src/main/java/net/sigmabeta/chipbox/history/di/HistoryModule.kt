package net.sigmabeta.chipbox.history.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.history.HistoryDatabase
import net.sigmabeta.chipbox.history.PlaybackHistoryRecorder
import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.history.real.RealPlaybackHistoryRecorder
import net.sigmabeta.chipbox.history.real.RealPlaybackHistoryRepository
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@BindingContainer
@ContributesTo(AppScope::class)
object HistoryModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackHistoryRepository(
        database: HistoryDatabase,
        hatchet: Hatchet,
    ): PlaybackHistoryRepository = RealPlaybackHistoryRepository(
        database.songPlayDao(),
        database.songPlayCountDao(),
        database.gamePlayCountDao(),
        database.artistPlayCountDao(),
        hatchet,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackHistoryRecorder(
        director: Director,
        repository: PlaybackHistoryRepository,
        hatchet: Hatchet,
    ): PlaybackHistoryRecorder = RealPlaybackHistoryRecorder(director, repository, hatchet)
}
