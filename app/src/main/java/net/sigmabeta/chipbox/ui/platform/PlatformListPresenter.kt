package net.sigmabeta.chipbox.ui.platform

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.FragmentPresenter
import javax.inject.Inject

@ActivityScoped
class PlatformListPresenter @Inject constructor(val database: SongDatabaseHelper) : FragmentPresenter() {
    var view: PlatformListView? = null

    val platformList = arrayListOf(
            Platform(Track.Companion.PLATFORM_GENESIS.toLong(), R.string.platform_name_genesis, 0),
            Platform(Track.Companion.PLATFORM_32X.toLong(), R.string.platform_name_32x, 0),
            Platform(Track.Companion.PLATFORM_SNES.toLong(), R.string.platform_name_snes, 0),
            Platform(Track.Companion.PLATFORM_NES.toLong(), R.string.platform_name_nes, 0),
            Platform(Track.Companion.PLATFORM_GAMEBOY.toLong(), R.string.platform_name_gameboy, 0)
    )

    fun onItemClick(id: Long) {
        view?.launchNavActivity(id)
    }

    /**
     * FragmentPresenter
     */
    override fun setup(arguments: Bundle?) {
    }

    override fun onReCreate(savedInstanceState: Bundle) {
    }

    override fun teardown() {
    }

    override fun updateViewState() {
        view?.setList(platformList)
    }

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is PlatformListView) this.view = view
    }

    override fun clearView() {
        view = null
    }
}