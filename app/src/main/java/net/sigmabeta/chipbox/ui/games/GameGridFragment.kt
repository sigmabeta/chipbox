package net.sigmabeta.chipbox.ui.games

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_game_grid.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.backend.ScanService
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.*
import net.sigmabeta.chipbox.ui.game.GameActivity
import net.sigmabeta.chipbox.ui.util.GridSpaceDecoration
import net.sigmabeta.chipbox.util.*
import javax.inject.Inject

class GameGridFragment : BaseFragment<GameGridPresenter, GameListView>(), GameListView, ItemListView<GameViewHolder>, TopLevelFragment, NavigationFragment {
    lateinit var presenter: GameGridPresenter
        @Inject set

    var adapter = GameGridAdapter(this)

    /**
     * GameListView
     */

    override fun setGames(games: List<Game>) {
        adapter.dataset = games
    }

    override fun setTitle(platformName: String) {
        setActivityTitle(platformName)
    }

    override fun launchGameActivity(id: String, position: Int) {
        val activity = getBaseActivity()

        val holder = grid_games.findViewHolderForAdapterPosition(position) as GameViewHolder

        val shareableImageView = Pair(holder.getSharedImage(), "image_clicked_game")
        GameActivity.launch(activity,
                id,
                activity.getShareableNavBar(),
                activity.getShareableStatusBar(),
                shareableImageView
        )
    }

    override fun startRescan() {
        val intent = Intent(activity, ScanService::class.java)
        activity.startService(intent)
    }

    override fun showContent() = ifVisible {
        grid_games.fadeIn()
        loading_spinner.fadeOutGone()
        layout_empty_state.fadeOutGone()
    }

    override fun showEmptyState() = ifVisible {
        layout_empty_state.visibility = View.VISIBLE
        label_empty_state.fadeInFromZero().setStartDelay(300)
        button_empty_state.fadeInFromZero().setStartDelay(600)
        grid_games.fadeOutGone()
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

    override fun onItemClick(position: Int) {
        presenter.onItemClick(position)
    }

    /**
     * BaseFragment
     */

    override fun showLoadingState() = ifVisible {
        grid_games.fadeOutPartially()
        loading_spinner.fadeIn().setDuration(50)
        layout_empty_state.fadeOutGone()
    }

    override fun inject() {
        val container = activity
        if (container is BaseActivity<*, *>) {
            container.getFragmentComponent()?.inject(this)
        }
    }

    override fun getPresenterImpl(): GameGridPresenter {
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

    override fun getFragmentTag() = FRAGMENT_TAG

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.game_grid"

        val ARGUMENT_PLATFORM_NAME = "${FRAGMENT_TAG}.platform_name"

        fun newInstance(platformName: String?): GameGridFragment {
            val fragment = GameGridFragment()

            val arguments = Bundle()
            arguments.putString(ARGUMENT_PLATFORM_NAME, platformName)

            fragment.arguments = arguments

            return fragment
        }
    }
}
