package net.sigmabeta.chipbox.ui.platform

import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.model.repository.LibraryScanner
import net.sigmabeta.chipbox.ui.ListPresenter
import javax.inject.Inject

@ActivityScoped
class PlatformListPresenter @Inject constructor(val updater: UiUpdater,
                                                val scanner: LibraryScanner) : ListPresenter<PlatformListView, Platform, PlatformViewHolder>() {

    /**
     * ListPresenter
     */

    override fun onItemClick(position: Int) {
        if (scanner.state != LibraryScanner.STATE_SCANNING) {
            val name = list?.get(position)?.name ?: return
            view?.launchNavActivity(name)
        } else {
            view?.showScanningWaitMessage()
        }
    }

    override fun getLoadOperation() = repository.getPlatforms()
}