package net.sigmabeta.chipbox.ui

import android.os.Bundle
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.realm.OrderedCollectionChangeSet
import io.realm.RealmResults
import io.realm.rx.CollectionChange
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.className
import net.sigmabeta.chipbox.model.domain.ListItem
import timber.log.Timber

abstract class ListPresenter<V : ListView<T, VH>, T : ListItem, in VH : BaseViewHolder<*, *, *>> : FragmentPresenter<V>() {
    var list: List<T>? = null

    var changeset: OrderedCollectionChangeSet? = null
    /**
     * FragmentPresenter
     */

    override fun setup(arguments: Bundle?) {
        loadArguments(arguments)
        loadItems()
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = loadItems()

    override fun teardown() {
        list = null
        changeset = null
    }

    override fun onClick(id: Int) = when (id) {
        R.id.button_empty_state -> {
            view?.startRescan()
            state = UiState.LOADING
        }
        else -> Unit
    }

    override fun showReadyState() {
        view?.setList(list!!)

        changeset?.let {
            view?.animateChanges(it)
        }

        view?.showContent()
    }

    /**
     * Abstract functions
     */

    abstract fun onItemClick(position: Int)

    abstract fun getLoadOperation(): Observable<CollectionChange<RealmResults<T>>>

    /**
     * Implementation Details
     */

    protected open fun loadArguments(arguments: Bundle?) = Unit

    private fun loadItems() {
        state = UiState.LOADING

        val subscription = getLoadOperation()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            printBenchmark("${className()} items Loaded")

                            list = it.collection
                            changeset = it.changeset

                            if (it.collection.isNotEmpty()) {
                                Timber.v("Showing items.")
                                state = UiState.READY
                            } else {
                                if (it.collection.isLoaded) {
                                    Timber.v("No items to show.")
                                    state = UiState.EMPTY
                                } else {
                                    Timber.v("Query not actually ready yet.")
                                }
                            }
                        },
                        {
                            state = UiState.ERROR
                            view?.showErrorSnackbar("Error: ${it.message}", null, null)
                        }
                )

        subscriptions.add(subscription)
    }
}