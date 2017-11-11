package net.sigmabeta.chipbox.ui.games

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.events.*
import net.sigmabeta.chipbox.ui.FragmentPresenter
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ActivityScoped
class GameGridPresenter @Inject constructor(val updater: UiUpdater) : FragmentPresenter<GameListView>() {
    var platformName: String? = null

    var games: List<Game>? = null

    fun onItemClick(position: Int) {
        val id = games?.get(position)?.id ?: return
        view?.launchGameActivity(id, position)
    }

    fun refresh(arguments: Bundle) = setupHelper(arguments)

    /**
     * FragmentPresenter
     */

    override fun setup(arguments: Bundle?) {
        setupHelper(arguments)
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) {
        Timber.e("Recreate")

        if (games == null) {
            setupHelper(arguments)
        }
    }

    override fun teardown() {
        games = null
        platformName = null
    }

    override fun updateViewState() {
        games?.let {
            if (it.isNotEmpty()) {
                showContent(it)
            } else {
                showEmptyState()
            }
        }

        val subscription = updater.asObservable()
                .throttleFirst(5000, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> { /* no-op */
                        }
                        is PositionEvent -> { /* no-op */
                        }
                        is GameEvent -> { /* no-op */
                        }
                        is StateEvent -> { /* no-op */
                        }
                        is FileScanEvent -> loadGames()
                        is FileScanCompleteEvent -> loadGames()
                        is FileScanFailedEvent -> { /* no-op */
                        }
                        else -> Timber.w("Unhandled %s", it.toString())
                    }
                }

        subscriptions.add(subscription)
    }

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_empty_state -> {
                view?.startRescan()
                loading = true
            }
        }
    }

    private fun setupHelper(arguments: Bundle?) {
        platformName = arguments?.getString(GameGridFragment.ARGUMENT_PLATFORM_NAME) ?: null

        platformName?.let {
            view?.setTitle(it)
        }

        loading = true

        loadGames()
    }

    private fun loadGames() {

        val request = platformName?.let {
            repository.getGamesForPlatform(it)
        } ?: let {
            repository.getGames()
        }

        val subscription = request
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            printBenchmark("Games Loaded")
                            loading = false
                            games = it

                            if (it.isNotEmpty()) {
                                showContent(it)
                            } else {
                                showEmptyState()
                            }
                        },
                        {
                            loading = false
                            showEmptyState()
                            view?.showErrorSnackbar("Error: ${it.message}", null, null)
                        }
                )

        subscriptions.add(subscription)
    }

    private fun showContent(games: List<Game>) {
        view?.setGames(games)
        view?.showContent()
    }

    private fun showEmptyState() {
        view?.showEmptyState()
    }
}