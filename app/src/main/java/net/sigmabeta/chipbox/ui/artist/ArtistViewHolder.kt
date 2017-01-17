package net.sigmabeta.chipbox.ui.artist

import android.view.View
import kotlinx.android.synthetic.main.list_item_artist.view.*
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.BaseViewHolder

class ArtistViewHolder(view: View, adapter: ArtistListAdapter) : BaseViewHolder<Artist, ArtistViewHolder, ArtistListAdapter>(view, adapter) {
    override fun bind(toBind: Artist) {
        view.text_artist_name.text = toBind.name
    }
}