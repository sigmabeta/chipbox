package net.sigmabeta.chipbox.view.fragment

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.android.synthetic.fragment_platform_list.frame_content
import kotlinx.android.synthetic.fragment_platform_list.list_platforms
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.FragmentInjector
import net.sigmabeta.chipbox.model.objects.Platform
import net.sigmabeta.chipbox.presenter.PlatformListPresenter
import net.sigmabeta.chipbox.view.activity.NavigationActivity
import net.sigmabeta.chipbox.view.adapter.PlatformListAdapter
import net.sigmabeta.chipbox.view.interfaces.PlatformListView
import java.util.*
import javax.inject.Inject

class PlatformListFragment : BaseFragment(), PlatformListView {
    var presenter: PlatformListPresenter? = null
        @Inject set

    val adapter = PlatformListAdapter(this)

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater?.inflate(R.layout.fragment_platform_list, container, false)

        presenter?.onCreateView()

        return rootView
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(activity)

        list_platforms.adapter = adapter
        list_platforms.layoutManager = layoutManager
    }

    /**
     * PlatformListView
     */

    override fun onItemClick(id: Long, stringId: Int) {
        NavigationActivity.launch(activity,
                GameGridFragment.FRAGMENT_TAG,
                id,
                getString(stringId))
    }

    override fun setList(list: ArrayList<Platform>) {
        adapter.setData(list)
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

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.platform_list"

        fun newInstance(): PlatformListFragment {
            val fragment = PlatformListFragment()

            return fragment
        }
    }
}