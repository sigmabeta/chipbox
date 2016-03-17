package net.sigmabeta.chipbox.ui.games

import android.view.View
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.ui.BaseArrayAdapter
import net.sigmabeta.chipbox.ui.ItemListView

class GameGridAdapter(view: ItemListView<GameViewHolder>) : BaseArrayAdapter<Game, GameViewHolder>(view) {
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
