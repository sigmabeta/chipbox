package net.sigmabeta.chipbox.view.adapter

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Artist
import net.sigmabeta.chipbox.view.interfaces.ItemListView
import net.sigmabeta.chipbox.view.viewholder.ArtistViewHolder

class ArtistListAdapter(view: ItemListView) : BaseArrayAdapter<Artist, ArtistViewHolder>(view) {
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