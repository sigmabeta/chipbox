package net.sigmabeta.chipbox.ui.platform

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.FragmentPresenter
import javax.inject.Inject

@ActivityScoped
class PlatformListPresenter @Inject constructor() : FragmentPresenter<PlatformListView>() {
    val platformList = arrayListOf(Platform("Test"))


    fun onItemClick(position: Int) {
        val id = platformList.get(position).id ?: return
        view?.launchNavActivity(id)
    }

    /**
     * FragmentPresenter
     */

    override fun setup(arguments: Bundle?) {
        needsSetup = false
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun teardown() = Unit

    override fun onClick(id: Int) = Unit

    override fun updateViewState() {
        view?.setList(platformList)
    }
}