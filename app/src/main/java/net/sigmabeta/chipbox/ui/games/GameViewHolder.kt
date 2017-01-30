package net.sigmabeta.chipbox.ui.games

import android.view.View
import kotlinx.android.synthetic.main.grid_item_game.view.*
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.BaseViewHolder
import net.sigmabeta.chipbox.util.loadImageLowQuality

class GameViewHolder(view: View, adapter: GameGridAdapter) : BaseViewHolder<Game, GameViewHolder, GameGridAdapter>(view, adapter) {
    var gameId: String? = null

    override fun bind(toBind: Game) {
        gameId = toBind.id

        view.text_game_title.text = toBind.title
        view.text_artist.text = toBind.artist?.name

        toBind.artLocal?.let {
            view.image_game_box_art.loadImageLowQuality(it, true, true)
        } ?: let {
            view.image_game_box_art.loadImageLowQuality(Game.PICASSO_ASSET_ALBUM_ART_BLANK, true, true)
        }
    }

    fun getSharedImage(): View = view.image_game_box_art
}