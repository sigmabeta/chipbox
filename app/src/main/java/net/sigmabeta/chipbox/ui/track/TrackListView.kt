package net.sigmabeta.chipbox.ui.track

import io.realm.OrderedCollectionChangeSet
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView

interface TrackListView : BaseView {
    fun setTracks(tracks: List<Track>)

    fun animateChanges(changeset: OrderedCollectionChangeSet)

    fun refreshList()

    fun setActivityTitle(title: String)

    fun startRescan()

    fun onTrackLoadError()
}