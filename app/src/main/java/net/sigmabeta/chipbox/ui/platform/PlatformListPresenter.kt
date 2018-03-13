package net.sigmabeta.chipbox.ui.platform

import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.ui.ListPresenter
import javax.inject.Inject

@ActivityScoped
class PlatformListPresenter @Inject constructor(val updater: UiUpdater) : ListPresenter<PlatformListView, Platform, PlatformViewHolder>() {

    /**
     * ListPresenter
     */

    override fun onItemClick(position: Int) {
        val name = list?.get(position)?.name ?: return
        view?.launchNavActivity(name)
    }

    override fun getLoadOperation() = repository.getPlatforms()
}