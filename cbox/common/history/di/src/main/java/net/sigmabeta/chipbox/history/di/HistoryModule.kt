package net.sigmabeta.chipbox.history.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.HistorySource
import net.sigmabeta.chipbox.history.HistoryDatabase
import net.sigmabeta.chipbox.history.PlaybackHistoryRecorder
import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.history.fake.FakePlaybackHistoryRepository
import net.sigmabeta.chipbox.history.real.RealPlaybackHistoryRecorder
import net.sigmabeta.chipbox.history.real.RealPlaybackHistoryRepository
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@BindingContainer
@ContributesTo(AppScope::class)
object HistoryModule {
    // The debug menu can swap the Room-backed store for an in-memory fake. Read once here at graph
    // build (so it applies on the next launch). The recorder below reuses whichever is bound.
    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackHistoryRepository(
        database: HistoryDatabase,
        debugSettingsManager: DebugSettingsManager,
        hatchet: Hatchet,
    ): PlaybackHistoryRepository = when (runBlocking { debugSettingsManager.getHistorySource().first() }) {
        HistorySource.REAL -> RealPlaybackHistoryRepository(
            database.songPlayDao(),
            database.songPlayCountDao(),
            database.gamePlayCountDao(),
            database.artistPlayCountDao(),
            hatchet,
        )

        HistorySource.FAKE -> FakePlaybackHistoryRepository()
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackHistoryRecorder(
        director: Director,
        repository: PlaybackHistoryRepository,
        hatchet: Hatchet,
    ): PlaybackHistoryRecorder = RealPlaybackHistoryRecorder(director, repository, hatchet)
}
