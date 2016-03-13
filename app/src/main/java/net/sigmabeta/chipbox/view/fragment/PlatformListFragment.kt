package net.sigmabeta.chipbox.view.fragment

import android.support.v7.widget.LinearLayoutManager
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.fragment_platform_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.FragmentInjector
import net.sigmabeta.chipbox.model.objects.Platform
import net.sigmabeta.chipbox.presenter.FragmentPresenter
import net.sigmabeta.chipbox.presenter.PlatformListPresenter
import net.sigmabeta.chipbox.util.isScrolledToBottom
import net.sigmabeta.chipbox.view.activity.NavigationActivity
import net.sigmabeta.chipbox.view.adapter.PlatformListAdapter
import net.sigmabeta.chipbox.view.interfaces.ItemListView
import net.sigmabeta.chipbox.view.interfaces.PlatformListView
import net.sigmabeta.chipbox.view.interfaces.TopLevelFragment
import java.util.*
import javax.inject.Inject

class PlatformListFragment : BaseFragment(), PlatformListView, ItemListView, TopLevelFragment {
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

    /**
     * ItemListView
     */

    override fun onItemClick(id: Long) {
        presenter.onItemClick(id)
    }

    /**
     * BaseFragment
     */

    override fun inject() {
        FragmentInjector.inject(this)
    }

    override fun getContentLayout(): FrameLayout {
        return frame_content
    }

    override fun getTitle(): String {
        return getString(R.string.app_name)
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

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.platform_list"

        fun newInstance(): PlatformListFragment {
            val fragment = PlatformListFragment()

            return fragment
        }
    }
}