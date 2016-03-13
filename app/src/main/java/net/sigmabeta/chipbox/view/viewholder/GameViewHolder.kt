package net.sigmabeta.chipbox.view.viewholder

import android.view.View
import kotlinx.android.synthetic.main.grid_item_game.view.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.util.loadImageLowQuality
import net.sigmabeta.chipbox.view.adapter.BaseViewHolder
import net.sigmabeta.chipbox.view.adapter.GameGridAdapter

class GameViewHolder(view: View, adapter: GameGridAdapter) : BaseViewHolder<Game, GameViewHolder, GameGridAdapter>(view, adapter), View.OnClickListener {
    var gameId: Long? = null

    override fun getId(): Long? {
        return gameId
    }

    override fun bind(toBind: Game) {
        gameId = toBind.id

        view.text_game_title.text = toBind.title
        view.text_company.text = toBind.company

        toBind.artLocal?.let {
            view.image_game_box_art.loadImageLowQuality(it)
        } ?: let {
            view.image_game_box_art.loadImageLowQuality(R.drawable.img_album_art_blank)
        }
    }
}