package net.sigmabeta.chipbox.playlists.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.playlists.PlaylistsDatabase
import net.sigmabeta.chipbox.playlists.PlaylistsRepository
import net.sigmabeta.chipbox.playlists.real.RealPlaylistsRepository
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@BindingContainer
@ContributesTo(AppScope::class)
object PlaylistsModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providePlaylistsRepository(
        database: PlaylistsDatabase,
        hatchet: Hatchet,
    ): PlaylistsRepository = RealPlaylistsRepository(
        database.playlistDao(),
        database.playlistTrackDao(),
        hatchet,
    )
}
