package net.sigmabeta.chipbox.ui.platform

import android.os.Bundle
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.model.events.FileScanCompleteEvent
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.ui.UiState
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

@ActivityScoped
class PlatformListPresenter @Inject constructor(val updater: UiUpdater) : FragmentPresenter<PlatformListView>() {
    var platformList: List<Platform>? = null

    private var scannerSubscription: Subscription? = null

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

    override fun showReadyState() {
        view?.setList(platformList!!)
        view?.showContent()

        listenForFileScans()
    }

    /**
     * Private Methods
     */

    private fun setupHelper(arguments: Bundle?) {
        loadPlatforms()
    }

    private fun loadPlatforms() {
        state = UiState.LOADING

        val subscription = repository.getPlatforms()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            printBenchmark("Platforms Loaded")

                            platformList = it

                            if (it.isNotEmpty()) {
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
        if (scannerSubscription?.isUnsubscribed == false) {
            scannerSubscription?.unsubscribe()
        }

        scannerSubscription = updater.asObservable()
                .filter { it is FileScanCompleteEvent }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    loadPlatforms()
                }

        subscriptions.add(scannerSubscription)
    }
}