package net.sigmabeta.chipbox.ui.track

import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.ListView

interface TrackListView : ListView<Track, TrackViewHolder> {
    fun setActivityTitle(title: String)
}