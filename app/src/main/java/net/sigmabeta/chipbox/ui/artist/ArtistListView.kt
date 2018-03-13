package net.sigmabeta.chipbox.ui.artist

import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.ListView

interface ArtistListView : ListView<Artist, ArtistViewHolder> {
    fun launchNavActivity(id: String)
}