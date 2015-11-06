package net.sigmabeta.chipbox.view.viewholder

import android.database.Cursor
import android.support.v7.widget.RecyclerView
import android.view.View
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.grid_item_game.view.image_game_box_art
import kotlinx.android.synthetic.grid_item_game.view.text_company
import kotlinx.android.synthetic.grid_item_game.view.text_game_title
import net.sigmabeta.chipbox.model.database.COLUMN_DB_ID
import net.sigmabeta.chipbox.model.database.COLUMN_GAME_TITLE
import net.sigmabeta.chipbox.view.adapter.GameGridAdapter

class GameViewHolder(val view: View, val adapter: GameGridAdapter) : RecyclerView.ViewHolder(view), View.OnClickListener {
    var gameId: Long? = null

    init {
        view.setOnClickListener(this)
    }

    fun bind(toBind: Cursor) {
        val gameTitle = toBind.getString(COLUMN_GAME_TITLE)

        gameId = toBind.getLong(COLUMN_DB_ID)

        view.text_game_title.text = gameTitle
        view.text_company.text = "Natsume"

        val imagePath = adapter.imagesPath + gameId.toString() + "/local.png"

        Picasso.with(view.context)
                .load(imagePath)
                .centerCrop()
                .fit()
                .into(view.image_game_box_art)
    }


    override fun onClick(v: View) {
        adapter.onItemClick(gameId ?: return)
    }
}