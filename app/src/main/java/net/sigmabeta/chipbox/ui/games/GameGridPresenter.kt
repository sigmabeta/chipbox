package net.sigmabeta.chipbox.ui.games

import android.os.Bundle
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.realm.OrderedCollectionChangeSet
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.events.FileScanCompleteEvent
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.ui.UiState
import timber.log.Timber
import javax.inject.Inject

@ActivityScoped
class GameGridPresenter @Inject constructor(val updater: UiUpdater) : FragmentPresenter<GameListView>() {
    var platformName: String? = null

    var games: List<Game>? = null

    var changeset: OrderedCollectionChangeSet? = null

    private var scannerSubscription: Disposable? = null

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

    override fun showReadyState() {
        view?.setGames(games!!)

        changeset?.let {
            view?.animateChanges(it)
        }

        view?.showContent()

        listenForFileScans()
    }

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_empty_state -> {
                view?.startRescan()
                state = UiState.LOADING
            }
        }
    }

    private fun setupHelper(arguments: Bundle?) {
        platformName = arguments?.getString(GameGridFragment.ARGUMENT_PLATFORM_NAME) ?: null

        platformName?.let {
            view?.setTitle(it)
        }

        loadGames()
    }

    private fun loadGames() {
        state = UiState.LOADING

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
                            games = it.collection
                            changeset = it.changeset

                            if (it.collection.isNotEmpty()) {
                                state = UiState.READY
                            } else {
                                state = UiState.EMPTY
                            }
                        },
                        {
                            state = UiState.ERROR
                            view?.showErrorSnackbar("Error: ${it.message}", null, null)
                        }
                )

        subscriptions.add(subscription)

        listenForFileScans()
    }

    // TODO Move into a "Top level presenter" superclass
    private fun listenForFileScans() {
        if (scannerSubscription?.isDisposed == false) {
            scannerSubscription?.dispose()
        }

        scannerSubscription = updater.asFlowable()
                .filter { it is FileScanCompleteEvent }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    loadGames()
                }

        subscriptions.add(scannerSubscription!!)
    }
}