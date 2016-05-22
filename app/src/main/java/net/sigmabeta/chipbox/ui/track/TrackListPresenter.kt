package net.sigmabeta.chipbox.ui.track

import android.os.Bundle
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.*
import javax.inject.Inject

@ActivityScoped
class TrackListPresenter @Inject constructor(val player: Player) : FragmentPresenter() {
    var view: TrackListView? = null

    var artistId = Artist.ARTIST_ALL

    var artist: Artist? = null

    var tracks: MutableList<Track>? = null

    var gameMap: HashMap<Long, Game>? = null

    fun onItemClick(position: Long) {
        tracks?.let {
            player.play(it, position.toInt())
        }
    }

    /**
     * FragmentPresenter
     */

    override fun setup(arguments: Bundle?) {
        setupHelper(arguments)
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) {
        if (tracks == null) {
            setupHelper(arguments)
        }
    }

    override fun teardown() {
        artistId = -1
        tracks = null
        gameMap = null
    }

    override fun updateViewState() {
        tracks?.let {
            view?.setTracks(it)
        }

        gameMap?.let {
            view?.setGames(it)
        }
    }

    override fun onClick(id: Int) = Unit

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is TrackListView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    private fun loadGames(tracks: List<Track>) {
        val subscription = Game.getFromTrackList(tracks)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            logInfo("[SongListPresenter] Loaded ${it.size} games.")
                            gameMap = it
                            view?.setGames(it)
                        }
                )

        subscriptions.add(subscription)
    }

    private fun setupHelper(arguments: Bundle?) {
        artistId = arguments?.getLong(TrackListFragment.ARGUMENT_ARTIST) ?: Artist.ARTIST_ALL

        if (artistId == Artist.ARTIST_ALL) {
            val tracksLoad = Track.getAll()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            {
                                logInfo("[SongListPresenter] Loaded ${it.size} tracks.")

                                tracks = it
                                view?.setTracks(it)
                                loadGames(it)
                            },
                            {
                                view?.showErrorSnackbar("Error: ${it.message}", null, null)
                            }
                    )

            subscriptions.add(tracksLoad)
        } else {
            val artistLoad = Artist.get(artistId)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            {
                                this.artist = it
                                view?.setActivityTitle(it.name ?: "Unknown Artist")

                                val tracks = it.getTracks()
                                view?.setTracks(tracks)
                                this.tracks = tracks
                                loadGames(tracks)
                            },
                            {
                                logError("[SongListPresenter] Error: ${it.message}")
                                view?.showErrorSnackbar("Error: ${it.message}", null, null)
                            }
                    )

            subscriptions.add(artistLoad)
        }
    }
}