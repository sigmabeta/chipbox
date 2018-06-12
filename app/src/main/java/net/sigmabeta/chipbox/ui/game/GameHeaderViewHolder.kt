package net.sigmabeta.chipbox.ui.game

import android.view.View
import kotlinx.android.synthetic.main.list_header_game.*
import net.sigmabeta.chipbox.model.repository.RealmRepository
import timber.log.Timber

class GameHeaderViewHolder(view: View, adapter: GameTrackListAdapter) : GameTrackViewHolder(view, adapter) {

    fun bind() {
        adapter.game?.let {
           text_title.text = it.title ?: RealmRepository.GAME_UNKNOWN
           text_subtitle.text = it.artist?.name ?: RealmRepository.ARTIST_UNKNOWN
           header_text_game_track_count.text = "${adapter.dataset?.size} tracks"

           header_text_game_platform.setText(it.platformName)
        }
    }

    init {
        Timber.i("Creating headerholder.")
    }
}