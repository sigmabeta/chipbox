package net.sigmabeta.chipbox.ui.artist

import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.BaseView
import java.util.*

interface ArtistListView : BaseView {
    fun setArtists(artists: ArrayList<Artist>)

    fun launchNavActivity(id: Long)
}