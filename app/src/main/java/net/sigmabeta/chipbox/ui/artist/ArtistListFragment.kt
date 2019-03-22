package net.sigmabeta.chipbox.ui.artist

import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_list.*
import net.sigmabeta.chipbox.className
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.ListFragment
import net.sigmabeta.chipbox.ui.navigation.NavigationActivity
import net.sigmabeta.chipbox.ui.track.TrackListFragment
import timber.log.Timber

class ArtistListFragment : ListFragment<ArtistListPresenter, ArtistListView, Artist, ArtistViewHolder, ArtistListAdapter>(), ArtistListView {

    /**
     * ArtistListView
     */

    override fun launchNavActivity(id: String) {
        NavigationActivity.launch(activity!!,
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

    override fun inject(): Boolean {
        val container = activity
        if (container is BaseActivity<*, *>) {container.getFragmentComponent()?.let {
                it.inject(this)
                return true
            } ?: let {
                Timber.e("${className()} injection failure: ${container?.className()}'s FragmentComponent not valid.")
                return false
            }
        } else {
            Timber.e("${className()} injection failure: ${container?.className()} not valid.")
            return false
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