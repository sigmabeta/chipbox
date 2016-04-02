package net.sigmabeta.chipbox.dagger.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.ui.artist.ArtistListFragment
import net.sigmabeta.chipbox.ui.file.FileListFragment
import net.sigmabeta.chipbox.ui.games.GameGridFragment
import net.sigmabeta.chipbox.ui.platform.PlatformListFragment
import net.sigmabeta.chipbox.ui.player.PlayerControlsFragment
import net.sigmabeta.chipbox.ui.player.PlayerFragment
import net.sigmabeta.chipbox.ui.playlist.PlaylistFragment
import net.sigmabeta.chipbox.ui.track.TrackListFragment

@ActivityScoped
@Subcomponent
interface FragmentComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: TrackListFragment)
    fun inject(view: PlatformListFragment)
    fun inject(view: PlayerFragment)
    fun inject(view: GameGridFragment)
    fun inject(view: ArtistListFragment)
    fun inject(view: FileListFragment)
    fun inject(view: PlaylistFragment)
    fun inject(view: PlayerControlsFragment)
}