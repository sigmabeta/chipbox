package net.sigmabeta.chipbox.ui.platform

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.*
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.util.logWarning
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ActivityScoped
class PlatformListPresenter @Inject constructor(val updater: UiUpdater) : FragmentPresenter<PlatformListView>() {
    var platformList: List<Platform>? = null

    fun onItemClick(position: Int) {
        val id = platformList?.get(position)?.name ?: return
        view?.launchNavActivity(id)
    }

    /**
     * FragmentPresenter
     */

    override fun setup(arguments: Bundle?) {
        setupHelper(arguments)
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) {
        setupHelper(arguments)
    }

    override fun teardown() = Unit

    override fun onClick(id: Int) = Unit

    override fun updateViewState() {
        platformList?.let {
            showContent(it)
        } ?: let {
            view?.requestReSetup()
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
                        is FileScanEvent -> loadPlatforms()
                        is FileScanCompleteEvent -> loadPlatforms()
                        is FileScanFailedEvent -> { /* no-op */
                        }
                        else -> logWarning("[PlayerFragmentPresenter] Unhandled ${it}")
                    }
                }

        subscriptions.add(subscription)
    }

    /**
     * Private Methods
     */

    private fun setupHelper(arguments: Bundle?) {
        loadPlatforms()
    }

    private fun loadPlatforms() {
        val subscription = repository.getPlatforms()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            printBenchmark("Platforms Loaded")
                            loading = false
                            platformList = it

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

    private fun showContent(platforms: List<Platform>) = view?.setList(platforms)

    private fun showEmptyState() {
        // TODO
    }
}