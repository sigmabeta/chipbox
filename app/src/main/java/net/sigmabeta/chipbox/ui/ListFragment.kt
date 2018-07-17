package net.sigmabeta.chipbox.ui

import android.support.annotation.CallSuper
import android.view.View
import android.view.View.VISIBLE
import io.realm.OrderedCollectionChangeSet
import kotlinx.android.synthetic.main.fragment_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.className
import net.sigmabeta.chipbox.model.domain.ListItem
import net.sigmabeta.chipbox.util.animation.*
import javax.inject.Inject

abstract class ListFragment<P : ListPresenter<V, T, VH>,
        V : ListView<T, VH>,
        T : ListItem,
        VH: BaseViewHolder<T, VH, A>,
        A : BaseArrayAdapter<T, VH>>
    : BaseFragment<P, V>(), ListView<T, VH> {
    lateinit var presenter: P
        @Inject set

    val adapter = createAdapter()

    /**
     * ListView
     */

    override fun setList(list: List<T>) {
        adapter.dataset = list
    }

    override fun animateChanges(changeset: OrderedCollectionChangeSet) {
        adapter.processChanges(changeset)
    }
    override fun onItemClick(position: Int) {
        presenter.onItemClick(position)
    }

    override fun startRescan() {
        (activity as ChromeActivity<*, *>).startScanner()
    }

    override fun isScrolledToBottom(): Boolean {
        return recycler_list?.isScrolledToBottom() ?: false
    }

    /**
     * BaseFragment
     */

    override fun showLoadingState() = ifVisible {
        if (recycler_list?.visibility != VISIBLE) {
            loading_spinner.fadeIn().setDuration(50)
            recycler_list.fadeOutPartially()
            layout_empty_state.fadeOutGone()
        }
    }

    override fun showContent() = ifVisible(true)  {
        if (recycler_list?.visibility != VISIBLE) {
            recycler_list.fadeIn()
            loading_spinner.fadeOutGone()
            layout_empty_state.fadeOutGone()
        }
    }

    override fun showEmptyState() = ifVisible(true)  {
        if (label_empty_state?.visibility != VISIBLE) {
            layout_empty_state.fadeInFromZero()
            label_empty_state.fadeInFromZero().setStartDelay(300)
            button_empty_state.fadeInFromZero().setStartDelay(600)
            loading_spinner.fadeOutGone()
            recycler_list.fadeOutGone()
        }
    }

    @CallSuper
    override fun configureViews() {
        button_empty_state.setOnClickListener(this)
    }

    override fun getContentLayout() = frame_content

    override fun getPresenterImpl() = presenter

    override fun getLayoutId(): Int {
        return R.layout.fragment_list
    }

    override fun getSharedImage(): View? = null

    override fun getFragmentTag() = "${BuildConfig.APPLICATION_ID}.${className()}"

    /**
     * Abstract Functions
     */

    abstract fun createAdapter(): A
}