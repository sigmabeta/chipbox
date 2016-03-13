package net.sigmabeta.chipbox.view.viewholder

import android.view.View
import kotlinx.android.synthetic.main.list_item_artist.view.*
import net.sigmabeta.chipbox.model.objects.Artist
import net.sigmabeta.chipbox.view.adapter.ArtistListAdapter
import net.sigmabeta.chipbox.view.adapter.BaseViewHolder

class ArtistViewHolder(view: View, adapter: ArtistListAdapter) : BaseViewHolder<Artist, ArtistViewHolder, ArtistListAdapter>(view, adapter), View.OnClickListener {
    var artistId: Long? = null

    override fun getId(): Long? {
        return artistId
    }

    override fun bind(toBind: Artist) {
        artistId = toBind.id
        view.text_artist_name.text = toBind.name
    }
}