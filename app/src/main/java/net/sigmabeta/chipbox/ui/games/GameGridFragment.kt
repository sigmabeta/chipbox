package net.sigmabeta.chipbox.ui.games

import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_game_grid.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.*
import net.sigmabeta.chipbox.ui.game.GameActivity
import net.sigmabeta.chipbox.ui.main.MainView
import net.sigmabeta.chipbox.ui.util.GridSpaceDecoration
import net.sigmabeta.chipbox.util.*
import java.util.*
import javax.inject.Inject

class GameGridFragment : BaseFragment(), GameListView, ItemListView<GameViewHolder>, TopLevelFragment, NavigationFragment {
    lateinit var presenter: GameGridPresenter
        @Inject set

    var adapter = GameGridAdapter(this)

    var clickedViewHolder: GameViewHolder? = null

    /**
     * GameListView
     */

    override fun setGames(games: MutableList<Game>) {
        adapter.dataset = games
    }

    override fun setActivityTitle(titleResource: Int) {
        setActivityTitle(getString(titleResource))
    }

    override fun launchGameActivity(id: Long) {
        clickedViewHolder?.let {
            GameActivity.launch(activity, id, getShareableViews())
        } ?: let {
            GameActivity.launch(activity, id)
        }
    }

    override fun clearClickedViewHolder() {
        clickedViewHolder = null
    }

    override fun showFilesScreen() {
        val mainActivity = activity
        if (mainActivity is MainView) {
            mainActivity.launchFileListActivity()
        }
    }

    override fun showLoadingSpinner() = ifVisible {
        loading_spinner.fadeIn().setDuration(50)
    }

    override fun hideLoadingSpinner() = ifVisible {
        loading_spinner.fadeOut()
    }

    override fun showContent() = ifVisible {
        grid_games.fadeIn()
    }

    override fun hideContent() = ifVisible {
        grid_games.fadeOutPartially()
    }

    override fun showEmptyState() = ifVisible {
        layout_empty_state.visibility = View.VISIBLE
        label_empty_state.fadeIn().setStartDelay(300)
        button_empty_state.fadeIn().setStartDelay(600)
    }

    override fun hideEmptyState() = ifVisible {
        layout_empty_state.fadeOut().withEndAction {
            label_empty_state.alpha = 0.0f
            button_empty_state.alpha = 0.0f
        }
    }

    /**
     * TopLevelFragment
     */

    override fun isScrolledToBottom(): Boolean {
        return grid_games?.isScrolledToBottom() ?: false
    }

    override fun refresh() = presenter.refresh(arguments)

    override fun getContentLayout(): ViewGroup {
        return frame_content
    }

    /**
     * ItemListView
     */

    override fun onItemClick(id: Long, clickedViewHolder: GameViewHolder) {
        this.clickedViewHolder = clickedViewHolder
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

    override fun getShareableViews(): Array<Pair<View, String>>? {
        val views = ArrayList<Pair<View, String>>(2)

        views.add(Pair(clickedViewHolder!!.getSharedImage(), "image_clicked_game"))

        activity.getShareableNavBar()?.let {
            views.add(it)
        }

        return views.toTypedArray()
    }

    override fun getPresenter(): FragmentPresenter {
        return presenter
    }

    override fun getLayoutId(): Int {
        return R.layout.fragment_game_grid
    }

    override fun configureViews() {
        val spacing = convertDpToPx(1.0f, activity).toInt()
        val columnCount = resources.getInteger(R.integer.columns_game_grid)
        val layoutManager = GridLayoutManager(activity, columnCount)

        grid_games.adapter = adapter
        grid_games.addItemDecoration(GridSpaceDecoration(spacing))
        grid_games.layoutManager = layoutManager

        // TODO We should only do this during animations
        grid_games.clipChildren = false

        button_empty_state.setOnClickListener(this)
    }

    override fun getSharedImage(): View? = null

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.game_grid"

        val ARGUMENT_PLATFORM_INDEX = "${FRAGMENT_TAG}.platform_index"

        fun newInstance(platformIndex: Long): GameGridFragment {
            val fragment = GameGridFragment()

            val arguments = Bundle()
            arguments.putLong(ARGUMENT_PLATFORM_INDEX, platformIndex)

            fragment.arguments = arguments

            return fragment
        }
    }
}
