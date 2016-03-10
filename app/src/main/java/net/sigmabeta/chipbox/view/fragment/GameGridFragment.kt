package net.sigmabeta.chipbox.view.fragment

import android.database.Cursor
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.fragment_game_grid.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.FragmentInjector
import net.sigmabeta.chipbox.presenter.FragmentPresenter
import net.sigmabeta.chipbox.presenter.GameGridPresenter
import net.sigmabeta.chipbox.util.convertDpToPx
import net.sigmabeta.chipbox.util.isScrolledToBottom
import net.sigmabeta.chipbox.view.GridSpaceDecoration
import net.sigmabeta.chipbox.view.activity.GameActivity
import net.sigmabeta.chipbox.view.adapter.GameGridAdapter
import net.sigmabeta.chipbox.view.interfaces.GameListView
import net.sigmabeta.chipbox.view.interfaces.NavigationFragment
import net.sigmabeta.chipbox.view.interfaces.TopLevelFragment
import javax.inject.Inject

class GameGridFragment : BaseFragment(), GameListView, TopLevelFragment, NavigationFragment {
    lateinit var presenter: GameGridPresenter
        @Inject set

    var adapter: GameGridAdapter? = null

    /**
     * TopLevelFragment
     */

    override fun isScrolledToBottom(): Boolean {
        return grid_games?.isScrolledToBottom() ?: false
    }

    override fun inject() {
        FragmentInjector.inject(this)
    }

    override fun setCursor(cursor: Cursor) {
        adapter?.changeCursor(cursor)
    }

    override fun onItemClick(id: Long) {
        GameActivity.launch(activity, id)
    }

    override fun getContentLayout(): FrameLayout {
        return frame_content
    }

    override fun getTitle(): String {
        return getString(R.string.app_name)
    }

    /**
     * BaseFragment
     */

    override fun getPresenter(): FragmentPresenter {
        return presenter
    }

    override fun getLayoutId(): Int {
        return R.layout.fragment_game_grid
    }

    override fun configureViews() {
        val spacing = convertDpToPx(4.0f, activity).toInt()
        val columnCount = resources.getInteger(R.integer.columns_game_grid)
        val layoutManager = GridLayoutManager(activity, columnCount)

        val adapter = GameGridAdapter(this, activity)
        this.adapter = adapter

        grid_games.adapter = adapter
        grid_games.addItemDecoration(GridSpaceDecoration(spacing))
        grid_games.layoutManager = layoutManager
    }

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.game_grid"

        val ARGUMENT_PLATFORM_INDEX = "${FRAGMENT_TAG}.platform_index"

        fun newInstance(platformIndex: Int): GameGridFragment {
            val fragment = GameGridFragment()

            val arguments = Bundle()
            arguments.putInt(ARGUMENT_PLATFORM_INDEX, platformIndex)

            fragment.arguments = arguments

            return fragment
        }
    }
}
