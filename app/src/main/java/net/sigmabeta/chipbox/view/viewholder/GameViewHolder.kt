package net.sigmabeta.chipbox.view.viewholder

import android.database.Cursor
import android.graphics.Bitmap
import android.support.v7.widget.RecyclerView
import android.view.View
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.grid_item_game.view.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.database.COLUMN_DB_ID
import net.sigmabeta.chipbox.model.database.COLUMN_GAME_ART_LOCAL
import net.sigmabeta.chipbox.model.database.COLUMN_GAME_COMPANY
import net.sigmabeta.chipbox.model.database.COLUMN_GAME_TITLE
import net.sigmabeta.chipbox.view.adapter.GameGridAdapter

class GameViewHolder(val view: View, val adapter: GameGridAdapter) : RecyclerView.ViewHolder(view), View.OnClickListener {
    var gameId: Long? = null

    init {
        view.setOnClickListener(this)
    }

    fun bind(toBind: Cursor) {
        val gameTitle = toBind.getString(COLUMN_GAME_TITLE)
        val company = toBind.getString(COLUMN_GAME_COMPANY)

        gameId = toBind.getLong(COLUMN_DB_ID)

        view.text_game_title.text = gameTitle
        view.text_company.text = company

        val imagePath = toBind.getString(COLUMN_GAME_ART_LOCAL)

        if (imagePath != null) {
            Picasso.with(view.context)
                    .load(imagePath)
                    .config(Bitmap.Config.RGB_565)
                    .centerCrop()
                    .fit()
                    .error(R.drawable.img_album_art_blank)
                    .into(view.image_game_box_art)
        } else {
            Picasso.with(view.context)
                    .load(R.drawable.img_album_art_blank)
                    .config(Bitmap.Config.RGB_565)
                    .centerCrop()
                    .fit()
                    .into(view.image_game_box_art)
        }
    }


    override fun onClick(v: View) {
        adapter.onItemClick(gameId ?: return)
    }
}