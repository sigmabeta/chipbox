package net.sigmabeta.chipbox.ui.game

import android.view.View
import kotlinx.android.synthetic.main.list_header_game.view.*
import net.sigmabeta.chipbox.model.repository.RealmRepository

class GameHeaderViewHolder(view: View, adapter: GameTrackListAdapter) : GameTrackViewHolder(view, adapter) {
    fun bind() {
        adapter.game?.let {
            view.header_text_title.text = it.title ?: RealmRepository.GAME_UNKNOWN
            view.subheader_text_subtitle.text = it.artist?.name ?: RealmRepository.ARTIST_UNKNOWN
            view.header_text_game_track_count.text = "${adapter.dataset?.size} tracks"

            view.header_text_game_platform.setText(it.platformName)
        }
    }
}