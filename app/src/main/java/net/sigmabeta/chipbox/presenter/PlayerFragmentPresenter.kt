package net.sigmabeta.chipbox.presenter

import android.media.session.PlaybackState
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.FragmentScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.view.interfaces.PlayerFragmentView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

@FragmentScoped
class PlayerFragmentPresenter @Inject constructor(val view: PlayerFragmentView,
                                                  val database: SongDatabaseHelper,
                                                  val player: Player) {
    var trackId: Long = -1

    var track: Track? = null

    fun onCreate(trackId: Long) {
        this.trackId = trackId
    }

    fun onCreateView() {
        database.getTrack(trackId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            track = it

                            view.setTrackTitle(it.title)
                            view.setArtist(it.artist)
                            view.setGameTitle(it.gameTitle)
                            view.setGameBoxart(it.gameId)
                        },
                        {
                            logError("[PlayerFragmentPresenter] Database error: ${it.message}")
                        }
                )
    }

    fun onFabClick() {
        val localTrack = track

        if (localTrack != null) {
            when (player.state) {
                PlaybackState.STATE_PLAYING -> {
                    player.pause()
                }

                PlaybackState.STATE_PAUSED -> {
                    player.play()
                }

                PlaybackState.STATE_STOPPED -> {
                    player.play(localTrack)
                }
            }
        }
    }
}