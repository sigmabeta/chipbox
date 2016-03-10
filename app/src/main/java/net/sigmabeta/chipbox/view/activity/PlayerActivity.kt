package net.sigmabeta.chipbox.view.activity

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.activity_player.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.ActivityInjector
import net.sigmabeta.chipbox.presenter.ActivityPresenter
import net.sigmabeta.chipbox.presenter.PlayerActivityPresenter
import net.sigmabeta.chipbox.view.fragment.PlayerFragment
import net.sigmabeta.chipbox.view.interfaces.FragmentContainer
import net.sigmabeta.chipbox.view.interfaces.PlayerActivityView
import javax.inject.Inject

class PlayerActivity : BaseActivity(), PlayerActivityView, FragmentContainer {
    lateinit var presenter: PlayerActivityPresenter
        @Inject set

    /**
     * PlayerView
     */

    override fun showPlayerFragment() {
        var fragment = PlayerFragment.newInstance()

        supportFragmentManager.beginTransaction()
                .add(R.id.frame_fragment, fragment, PlayerFragment.FRAGMENT_TAG)
                .commit()
    }

    /**
     * FragmentContainer
     */

    override fun setActivityTitle(title: String) {
        setTitle(title)
    }

    /**
     * BaseActivity
     */

    override fun inject() {
        ActivityInjector.inject(this)
    }

    override fun getPresenter(): ActivityPresenter {
        return presenter
    }

    override fun configureViews() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_player
    }

    override fun getContentLayout(): FrameLayout {
        return frame_fragment
    }

    companion object {
        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.player"

        fun launch(context: Context) {
            val launcher = Intent(context, PlayerActivity::class.java)

            context.startActivity(launcher)
        }

        fun getLauncher(context: Context): Intent {
            return Intent(context, PlayerActivity::class.java)
        }
    }
}