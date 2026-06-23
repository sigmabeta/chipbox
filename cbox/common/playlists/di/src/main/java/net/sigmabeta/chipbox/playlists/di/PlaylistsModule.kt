package net.sigmabeta.chipbox.playlists.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.PlaylistsSource
import net.sigmabeta.chipbox.playlists.PlaylistsDatabase
import net.sigmabeta.chipbox.playlists.PlaylistsRepository
import net.sigmabeta.chipbox.playlists.fake.FakePlaylistsRepository
import net.sigmabeta.chipbox.playlists.real.RealPlaylistsRepository
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@BindingContainer
@ContributesTo(AppScope::class)
object PlaylistsModule {
    // The debug menu can swap the Room-backed store for an in-memory fake. Read once here at graph
    // build (so it applies on the next launch), mirroring the repository/generator/speaker switches.
    @Provides
    @SingleIn(AppScope::class)
    fun providePlaylistsRepository(
        database: PlaylistsDatabase,
        debugSettingsManager: DebugSettingsManager,
        hatchet: Hatchet,
    ): PlaylistsRepository = when (runBlocking { debugSettingsManager.getPlaylistsSource().first() }) {
        PlaylistsSource.REAL -> RealPlaylistsRepository(
            database.playlistDao(),
            database.playlistTrackDao(),
            hatchet,
        )

        PlaylistsSource.FAKE -> FakePlaylistsRepository()
    }
}
