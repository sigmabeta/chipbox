package net.sigmabeta.chipbox.presenter

import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.view.interfaces.SongListView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

@FragmentScoped
class SongListPresenter @Inject constructor(val view: SongListView,
                                            val database: SongDatabaseHelper,
                                            val player: Player) {
    var artist = Track.PLATFORM_ALL.toLong()

    fun onCreate(artist: Long) {
        this.artist = artist
    }

    fun onCreateView() {
        val readOperation = if (artist == Track.PLATFORM_ALL.toLong()) {
            database.getSongList()
        } else {
            database.getSongListForArtist(artist)
        }

        readOperation.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            view.setCursor(it)
                        }
                )
    }

    fun onItemClick(track: Track, position: Int) {
        if (artist == Track.PLATFORM_ALL.toLong()) {
            player.play(track)
            view.launchPlayerActivity()
        } else {
            val cursor = view.getCursor()

            if (cursor != null) {
                val queue = SongDatabaseHelper.getPlaybackQueueFromCursor(cursor)
                player.play(queue, position)
            }
        }
    }
}