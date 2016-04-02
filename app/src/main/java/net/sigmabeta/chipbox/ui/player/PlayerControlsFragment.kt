package net.sigmabeta.chipbox.ui.player

import android.view.View
import kotlinx.android.synthetic.main.fragment_player_controls.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.FragmentPresenter
import javax.inject.Inject

@ActivityScoped
class PlayerControlsFragment : BaseFragment(), PlayerControlsView, View.OnClickListener {
    lateinit var presenter: PlayerControlsPresenter
        @Inject set

    override fun onClick(clicked: View?) {
        clicked?.id?.let {
            presenter.onClick(it)
        }
    }

    /**
     * PlayerControlsView
     */

    override fun showPauseButton() {
        button_play.setImageResource(R.drawable.ic_pause_black_24dp)
    }

    override fun showPlayButton() {
        button_play.setImageResource(R.drawable.ic_play_arrow_black_24dp)
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

    override fun getPresenter(): FragmentPresenter = presenter

    override fun getLayoutId() = R.layout.fragment_player_controls

    override fun getContentLayout() = frame_content

    override fun getSharedImage() = null

    override fun configureViews() {
        button_play.setOnClickListener(this)
        button_skip_back.setOnClickListener(this)
        button_skip_forward.setOnClickListener(this)
        button_shuffle.setOnClickListener(this)
        button_repeat.setOnClickListener(this)
    }

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.player_controls"

        fun newInstance(): PlayerControlsFragment {
            val fragment = PlayerControlsFragment()
            return fragment
        }
    }
}