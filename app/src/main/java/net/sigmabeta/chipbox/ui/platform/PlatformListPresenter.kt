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
    val platformList = arrayListOf(
            Platform(Track.Companion.PLATFORM_GENESIS.toLong(), R.string.platform_name_genesis, 0),
            Platform(Track.Companion.PLATFORM_32X.toLong(), R.string.platform_name_32x, 0),
            Platform(Track.Companion.PLATFORM_SNES.toLong(), R.string.platform_name_snes, 0),
            Platform(Track.Companion.PLATFORM_NES.toLong(), R.string.platform_name_nes, 0),
            Platform(Track.Companion.PLATFORM_GAMEBOY.toLong(), R.string.platform_name_gameboy, 0)
    )

    fun onItemClick(position: Int) {
        val id = platformList.get(position).id
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