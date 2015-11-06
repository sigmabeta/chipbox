package net.sigmabeta.chipbox.presenter

import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.objects.Platform
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.view.interfaces.PlatformListView
import javax.inject.Inject

@FragmentScoped
class PlatformListPresenter @Inject constructor(val view: PlatformListView,
                                                val database: SongDatabaseHelper) {

    val platformList = arrayListOf(
            Platform(Track.PLATFORM_GENESIS.toLong(), R.string.platform_name_genesis, 0),
            Platform(Track.PLATFORM_SNES.toLong(), R.string.platform_name_snes, 0)
    )

    fun onCreateView() {
        view.setList(platformList)
    }
}