package net.sigmabeta.chipbox.view.interfaces

import net.sigmabeta.chipbox.model.objects.Artist
import java.util.*

interface ArtistListView : BaseView {
    fun setArtists(artists: ArrayList<Artist>)

    fun launchNavActivity(id: Long)
}