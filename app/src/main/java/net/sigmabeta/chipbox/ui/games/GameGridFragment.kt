package net.sigmabeta.chipbox.ui.games

import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.fragment_game_grid.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Game
import net.sigmabeta.chipbox.ui.*
import net.sigmabeta.chipbox.ui.game.GameActivity
import net.sigmabeta.chipbox.util.convertDpToPx
import net.sigmabeta.chipbox.util.isScrolledToBottom
import java.util.*
import javax.inject.Inject

class GameGridFragment : BaseFragment(), GameListView, ItemListView, TopLevelFragment, NavigationFragment {
    lateinit var presenter: GameGridPresenter
        @Inject set

    var adapter = GameGridAdapter(this)

    override fun setActivityTitle(titleResource: Int) {
        setActivityTitle(getString(titleResource))
    }

    /**
     * GameListView
     */

    override fun setGames(games: ArrayList<Game>) {
        adapter.dataset = games
    }

    override fun launchGameActivity(id: Long) {
        GameActivity.launch(activity, id)
    }

    /**
     * TopLevelFragment
     */

    override fun isScrolledToBottom(): Boolean {
        return grid_games?.isScrolledToBottom() ?: false
    }

    override fun getContentLayout(): FrameLayout {
        return frame_content
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
        val container = activity
        if (container is BaseActivity) {
            container.getFragmentComponent().inject(this)
        }
    }

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
