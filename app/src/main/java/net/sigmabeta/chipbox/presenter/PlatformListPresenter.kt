package net.sigmabeta.chipbox.presenter

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.objects.Platform
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.view.interfaces.BaseView
import net.sigmabeta.chipbox.view.interfaces.PlatformListView
import javax.inject.Inject

@FragmentScoped
class PlatformListPresenter @Inject constructor(val database: SongDatabaseHelper) : FragmentPresenter() {
    var view: PlatformListView? = null

    val platformList = arrayListOf(
            Platform(Track.PLATFORM_GENESIS.toLong(), R.string.platform_name_genesis, 0),
            Platform(Track.PLATFORM_32X.toLong(), R.string.platform_name_32x, 0),
            Platform(Track.PLATFORM_SNES.toLong(), R.string.platform_name_snes, 0),
            Platform(Track.PLATFORM_NES.toLong(), R.string.platform_name_nes, 0),
            Platform(Track.PLATFORM_GAMEBOY.toLong(), R.string.platform_name_gameboy, 0)
    )

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

    override fun setView(view: BaseView) {
        if (view is PlatformListView) this.view = view
    }

    override fun clearView() {
        view = null
    }
}