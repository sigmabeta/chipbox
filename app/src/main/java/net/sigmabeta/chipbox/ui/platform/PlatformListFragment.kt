package net.sigmabeta.chipbox.ui.platform

import android.support.v7.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_list.*
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.ListFragment
import net.sigmabeta.chipbox.ui.games.GameGridFragment
import net.sigmabeta.chipbox.ui.navigation.NavigationActivity

class PlatformListFragment : ListFragment<PlatformListPresenter, PlatformListView, Platform, PlatformViewHolder, PlatformListAdapter>(), PlatformListView {

    /**
     * PlatformListView
     */

    override fun launchNavActivity(platformName: String) {
        NavigationActivity.launch(activity,
                GameGridFragment.FRAGMENT_TAG,
                platformName)
    }

    /**
     * ListFragment
     */

    override fun createAdapter() = PlatformListAdapter(this)

    /**
     * BaseFragment
     */

    override fun inject() {
        val container = activity
        if (container is BaseActivity<*, *>) {
            container.getFragmentComponent()?.inject(this)
        }
    }

    override fun configureViews() {
        super.configureViews()

        val layoutManager = LinearLayoutManager(activity)

        recycler_list.adapter = adapter
        recycler_list.layoutManager = layoutManager
    }

    companion object {
        fun newInstance(): PlatformListFragment {
            val fragment = PlatformListFragment()

            return fragment
        }
    }
}