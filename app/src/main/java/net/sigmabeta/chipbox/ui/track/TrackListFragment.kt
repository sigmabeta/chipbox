package net.sigmabeta.chipbox.ui.track

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.className
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.ListFragment
import net.sigmabeta.chipbox.ui.NavigationFragment
import timber.log.Timber

class TrackListFragment : ListFragment<TrackListPresenter, TrackListView, Track, TrackViewHolder, TrackListAdapter>(), TrackListView, NavigationFragment {

    /**
     * TrackListView
     */

    /**
     * ListFragment
     */

    override fun createAdapter() = TrackListAdapter(this)

    /**
     * BaseFragment
     */

    override fun inject() : Boolean {
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

        button_empty_state.setOnClickListener(this)
    }

    override fun getFragmentTag() = FRAGMENT_TAG

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.track_list"

        val ARGUMENT_ARTIST = "${FRAGMENT_TAG}.artist"

        fun newInstance(id: String?): TrackListFragment {
            val fragment = TrackListFragment()

            val arguments = Bundle()
            arguments.putString(ARGUMENT_ARTIST, id)

            fragment.arguments = arguments
            return fragment
        }
    }
}