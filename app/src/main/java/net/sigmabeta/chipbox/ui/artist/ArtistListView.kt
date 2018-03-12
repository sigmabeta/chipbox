package net.sigmabeta.chipbox.ui.artist

import io.realm.OrderedCollectionChangeSet
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.BaseView

interface ArtistListView : BaseView {
    fun setArtists(artists: List<Artist>)

    fun animateChanges(changeset: OrderedCollectionChangeSet)

    fun launchNavActivity(id: String)

    fun startRescan()
}