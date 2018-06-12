package net.sigmabeta.chipbox.ui.games

import android.view.View
import kotlinx.android.synthetic.main.grid_item_game.*
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.BaseViewHolder
import net.sigmabeta.chipbox.util.loadImageLowQuality

class GameViewHolder(view: View, adapter: GameGridAdapter) : BaseViewHolder<Game, GameViewHolder, GameGridAdapter>(view, adapter) {
    var gameId: String? = null

    override fun bind(toBind: Game) {
        gameId = toBind.id

       text_title.text = toBind.title
       text_subtitle.text = toBind.artist?.name

        toBind.artLocal?.let {
           image_game.loadImageLowQuality(it, true, true)
        } ?: let {
           image_game.loadImageLowQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, true, true)
        }
    }

    fun getSharedImage() : View =image_game
    fun getSharedCard() : View =layout_background
    fun getSharedTitle() : View  =text_title
    fun getSharedSubtitle() : View =text_subtitle
}