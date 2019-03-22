package net.sigmabeta.chipbox.ui

import android.os.Bundle
import androidx.annotation.CallSuper
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.realm.OrderedCollectionChangeSet
import io.realm.RealmResults
import io.realm.rx.CollectionChange
import net.sigmabeta.chipbox.R
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

    @CallSuper
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
        if (subscriptions.isDisposed) {
            loadItems()
        }

        list?.let { checkedList ->
            view?.setList(checkedList)

            changeset?.let {
                if (checkedList.size > 15) {
                    view?.animateChanges(it)
                } else {
                    view?.refreshList()
                }
            }
        }


        view?.showContent()
    }

    /**
     * Abstract functions
     */

    abstract fun onItemClick(position: Int)

    abstract fun getLoadOperation(): Observable<CollectionChange<RealmResults<T>>>?

    protected open fun getLoadOperationWithoutDiffs(): Flowable<RealmResults<T>>? = null

    /**
     * Implementation Details
     */

    protected open fun loadArguments(arguments: Bundle?) = Unit

    private fun loadItems() {
        state = UiState.LOADING

        changeset = null

        // TODO Fix realm track list query problem
        var subscription = getLoadOperation()
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(
                        {
                            list = it.collection
                            changeset = it.changeset

                            if (list?.isNotEmpty() == true) {
                                printBenchmark("Items Loaded, size = ${it.collection.size}")

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
                            handleError(it)
                        }
                )

        if (subscription == null) {
            subscription = getLoadOperationWithoutDiffs()
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.subscribe(
                            {
                                list = it.toMutableList()

                                if (list?.isNotEmpty() == true) {
                                    printBenchmark("Items Loaded, size = ${it.size}")

                                    Timber.v("Showing items.")
                                    state = UiState.READY
                                } else {
                                    if (it.isLoaded) {
                                        Timber.v("No items to show.")
                                        state = UiState.EMPTY
                                    } else {
                                        Timber.v("Query not actually ready yet.")
                                    }
                                }
                            },
                            {
                                handleError(it)
                            }
                    )
        }

        if (subscription != null) {
            subscriptions.add(subscription)
        }
    }
}