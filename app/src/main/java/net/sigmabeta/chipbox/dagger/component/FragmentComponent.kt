package net.sigmabeta.chipbox.dagger.component

import dagger.Subcomponent
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.view.fragment.*

@FragmentScoped
@Subcomponent
interface FragmentComponent {
    /**
     * Crucial: injection targets must be the correct type.
     * Passing an interface here will result in a no-op injection.
     */
    fun inject(view: SongListFragment)
    fun inject(view: PlatformListFragment)
    fun inject(view: PlayerFragment)
    fun inject(view: GameGridFragment)
    fun inject(view: ArtistListFragment)

}