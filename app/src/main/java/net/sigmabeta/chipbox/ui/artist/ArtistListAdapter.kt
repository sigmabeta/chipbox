package net.sigmabeta.chipbox.ui.artist

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.BaseArrayAdapter
import net.sigmabeta.chipbox.ui.ItemListView

class ArtistListAdapter(view: ItemListView<ArtistViewHolder>) : BaseArrayAdapter<Artist, ArtistViewHolder>(view) {
    override fun getLayoutId(): Int {
        return R.layout.list_item_artist
    }

    override fun createViewHolder(view: View): ArtistViewHolder {
        return ArtistViewHolder(view, this)
    }

    override fun bind(holder: ArtistViewHolder, item: Artist) {
        holder.bind(item)
    }
}