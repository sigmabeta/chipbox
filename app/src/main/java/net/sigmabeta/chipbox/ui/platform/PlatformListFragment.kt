package net.sigmabeta.chipbox.ui.platform

import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_platform_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.ui.*
import net.sigmabeta.chipbox.ui.games.GameGridFragment
import net.sigmabeta.chipbox.ui.navigation.NavigationActivity
import net.sigmabeta.chipbox.util.isScrolledToBottom
import java.util.*
import javax.inject.Inject

class PlatformListFragment : BaseFragment(), PlatformListView, ItemListView<PlatformViewHolder>, TopLevelFragment {
    lateinit var presenter: PlatformListPresenter
        @Inject set

    val adapter = PlatformListAdapter(this)

    /**
     * PlatformListView
     */

    override fun setList(list: ArrayList<Platform>) {
        adapter.dataset = list
    }

    override fun launchNavActivity(id: Long) {
        NavigationActivity.launch(activity,
                GameGridFragment.FRAGMENT_TAG,
                id)
    }

    /**
     * TopLevelFragment
     */

    override fun isScrolledToBottom(): Boolean {
        return list_platforms?.isScrolledToBottom() ?: false
    }

    override fun refresh() = Unit

    /**
     * ItemListView
     */

    override fun onItemClick(position: Int, clickedViewHolder: PlatformViewHolder) {
        presenter.onItemClick(position)
    }

    /**
     * BaseFragment
     */

    override fun inject() {
        val container = activity
        if (container is BaseActivity) {
            container.getFragmentComponent().inject(this)
        }
    }

    override fun getContentLayout(): ViewGroup {
        return frame_content
    }

    override fun getPresenter(): FragmentPresenter {
        return presenter
    }

    override fun getLayoutId(): Int {
        return R.layout.fragment_platform_list
    }

    override fun configureViews() {
        val layoutManager = LinearLayoutManager(activity)

        list_platforms.adapter = adapter
        list_platforms.layoutManager = layoutManager
    }

    override fun getSharedImage(): View? = null

    override fun getFragmentTag() = FRAGMENT_TAG

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.platform_list"

        fun newInstance(): PlatformListFragment {
            val fragment = PlatformListFragment()

            return fragment
        }
    }
}