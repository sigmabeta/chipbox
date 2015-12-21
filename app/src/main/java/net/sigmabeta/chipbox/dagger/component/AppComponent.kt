package net.sigmabeta.chipbox.dagger.component

import dagger.Component
import net.sigmabeta.chipbox.backend.PlayerService
import net.sigmabeta.chipbox.backend.module.AudioModule
import net.sigmabeta.chipbox.dagger.module.AppModule
import net.sigmabeta.chipbox.model.database.module.DatabaseModule
import net.sigmabeta.chipbox.presenter.component.*
import net.sigmabeta.chipbox.presenter.module.*
import javax.inject.Singleton

@Singleton
@Component(
        modules = arrayOf(
                AppModule::class,
                AudioModule::class,
                DatabaseModule::class
        )
)
interface AppComponent {
    fun inject(backendView: PlayerService)

    fun plus(mainModule: MainModule): MainComponent

    fun plus(fileListModule: FileListModule): FileListComponent

    fun plus(navigationModule: NavigationModule): NavigationComponent

    fun plus(playerModule: PlayerActivityModule): PlayerActivityComponent

    fun plus(gameListModule: GameListModule): GameListComponent

    fun plus(artistListModule: ArtistListModule): ArtistListComponent

    fun plus(songListModule: SongListModule): SongListComponent

    fun plus(gameModule: GameModule): GameComponent

    fun plus(platformListModule: PlatformListModule): PlatformListComponent

    fun plus(playerFragmentModule: PlayerFragmentModule): PlayerFragmentComponent
}

