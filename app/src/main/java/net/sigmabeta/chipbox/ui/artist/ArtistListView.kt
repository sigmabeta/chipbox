package net.sigmabeta.chipbox.ui.artist

import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.BaseView

interface ArtistListView : BaseView {
    fun setArtists(artists: MutableList<Artist>)

    fun launchNavActivity(id: Long)
}