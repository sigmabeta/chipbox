package net.sigmabeta.chipbox.ui.game

import android.view.View
import kotlinx.android.synthetic.main.list_header_game.view.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.repository.RealmRepository

class GameHeaderViewHolder(view: View, adapter: GameTrackListAdapter) : GameTrackViewHolder(view, adapter) {
    fun bind() {
        adapter.game?.let {
            view.header_text_game_title.text = it.title ?: RealmRepository.GAME_UNKNOWN
            view.header_text_game_artist.text = it.artist?.name ?: RealmRepository.ARTIST_UNKNOWN
            view.header_text_game_track_count.text = "${adapter.dataset?.size} tracks"

            view.header_text_game_platform.setText(when (it.platform) {
                Track.PLATFORM_32X -> R.string.platform_name_32x
                Track.PLATFORM_GAMEBOY -> R.string.platform_name_gameboy
                Track.PLATFORM_GENESIS -> R.string.platform_name_genesis
                Track.PLATFORM_NES -> R.string.platform_name_nes
                Track.PLATFORM_SNES -> R.string.platform_name_snes
                Track.PLATFORM_OTHER -> R.string.platform_name_other
                else -> -1
            })
        }
    }
}