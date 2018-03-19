package net.sigmabeta.chipbox.ui.artist

import android.support.v7.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_list.*
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.ListFragment
import net.sigmabeta.chipbox.ui.navigation.NavigationActivity
import net.sigmabeta.chipbox.ui.track.TrackListFragment

class ArtistListFragment : ListFragment<ArtistListPresenter, ArtistListView, Artist, ArtistViewHolder, ArtistListAdapter>(), ArtistListView {

    /**
     * ArtistListView
     */

    override fun launchNavActivity(id: String) {
        NavigationActivity.launch(activity,
                TrackListFragment.FRAGMENT_TAG,
                id)
    }

    /**
     * ListFragment
     */

    override fun createAdapter() = ArtistListAdapter(this)

    /**
     * BaseFragment
     */

    override fun inject() {
        val container = activity
        if (container is BaseActivity<*, *>) {
            container.getFragmentComponent()?.inject(this) ?: activity.finish()
        }
    }

    override fun configureViews() {
        super.configureViews()

        val layoutManager = LinearLayoutManager(activity)

        recycler_list.adapter = adapter
        recycler_list.layoutManager = layoutManager
    }

    companion object {
        fun newInstance(): ArtistListFragment {
            val fragment = ArtistListFragment()

            return fragment
        }
    }
}