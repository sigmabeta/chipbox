package net.sigmabeta.chipbox.ui.games

import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.util.Pair
import kotlinx.android.synthetic.main.fragment_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.className
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.ListFragment
import net.sigmabeta.chipbox.ui.NavigationFragment
import net.sigmabeta.chipbox.ui.game.GameActivity
import net.sigmabeta.chipbox.ui.util.GridSpaceDecoration
import net.sigmabeta.chipbox.util.convertDpToPx
import timber.log.Timber

class GameGridFragment : ListFragment<GameGridPresenter, GameListView, Game, GameViewHolder, GameGridAdapter>(), GameListView, NavigationFragment {

    /**
     * GameListView
     */

    override fun launchGameActivity(id: String, position: Int) {
        val activity = getBaseActivity()

        val holder = recycler_list.findViewHolderForAdapterPosition(position) as GameViewHolder

        val shareableImageView = Pair(holder.getSharedImage(), "image_clicked_game")
        val shareableTitleView = Pair(holder.getSharedTitle(), "header_text_title")
        val shareableSubtitleView = Pair(holder.getSharedSubtitle(), "subheader_text_subtitle")
        val shareableCardView = Pair(holder.getSharedCard(), "card_clicked_game")
        GameActivity.launch(activity,
                id,
                activity.getShareableNavBar(),
                activity.getShareableStatusBar(),
                shareableImageView,
                shareableTitleView,
                shareableSubtitleView,
                shareableCardView)
    }

    override fun setTitle(platformName: String) {
        setActivityTitle(platformName)
    }

    /**
     * ListFragment
     */

    override fun createAdapter() = GameGridAdapter(this)

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

        val spacing = convertDpToPx(1.0f, activity!!).toInt()
        val columnCount = resources.getInteger(R.integer.columns_game_grid)
        val layoutManager = GridLayoutManager(activity, columnCount)

        recycler_list.adapter = adapter
        recycler_list.addItemDecoration(GridSpaceDecoration(spacing))
        recycler_list.layoutManager = layoutManager

        // TODO We should only do this during animations
        recycler_list.clipChildren = false
    }

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
