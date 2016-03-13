package net.sigmabeta.chipbox.view.adapter

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.view.interfaces.ItemListView
import net.sigmabeta.chipbox.view.viewholder.GameViewHolder

class GameGridAdapter(view: ItemListView) : BaseArrayAdapter<Game, GameViewHolder>(view) {
    override fun getLayoutId(): Int {
        return R.layout.grid_item_game
    }

    override fun createViewHolder(view: View): GameViewHolder {
        return GameViewHolder(view, this)
    }

    override fun bind(holder: GameViewHolder, item: Game) {
        holder.bind(item)
    }
}
