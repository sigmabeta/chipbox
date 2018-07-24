package net.sigmabeta.chipbox.ui.artist

import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.repository.LibraryScanner
import net.sigmabeta.chipbox.ui.ListPresenter
import javax.inject.Inject

@ActivityScoped
class ArtistListPresenter @Inject constructor(val updater: UiUpdater,
                                              val scanner: LibraryScanner) : ListPresenter<ArtistListView, Artist, ArtistViewHolder>() {

    /**
     *  ListPresenter
     */

    override fun onItemClick(position: Int) {
        if (scanner.state != LibraryScanner.STATE_SCANNING) {
            val id = list?.get(position)?.id ?: return
            view?.launchNavActivity(id)
        } else {
            view?.showScanningWaitMessage()
        }
    }

    override fun getLoadOperation() = repository.getArtists()
}