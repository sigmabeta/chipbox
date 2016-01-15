package net.sigmabeta.chipbox.view.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_navigation.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.ActivityInjector
import net.sigmabeta.chipbox.presenter.NavigationPresenter
import net.sigmabeta.chipbox.view.fragment.BaseFragment
import net.sigmabeta.chipbox.view.fragment.GameGridFragment
import net.sigmabeta.chipbox.view.fragment.SongListFragment
import net.sigmabeta.chipbox.view.interfaces.FragmentContainer
import net.sigmabeta.chipbox.view.interfaces.NavigationView
import javax.inject.Inject

class NavigationActivity : BaseActivity(), NavigationView, FragmentContainer {
    var presenter: NavigationPresenter? = null
        @Inject set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_navigation)

        setSupportActionBar(toolbar_navigation)

        val launcher = intent

        val fragmentTag = launcher.getStringExtra(ARGUMENT_FRAGMENT_TAG)
        val fragmentArg = launcher.getLongExtra(ARGUMENT_FRAGMENT_ARG, -1)
        val title = launcher.getStringExtra(ARGUMENT_TITLE)

        setTitle(title)
        presenter?.onCreate(fragmentTag, fragmentArg, savedInstanceState)
    }

    /**
     * NavigationView
     */

    override fun showFragment(fragmentTag: String, fragmentArg: Long) {
        var fragment: BaseFragment

        when (fragmentTag) {
            GameGridFragment.FRAGMENT_TAG -> fragment = GameGridFragment.newInstance(fragmentArg.toInt())
            SongListFragment.FRAGMENT_TAG -> fragment = SongListFragment.newInstance(fragmentArg)
            else -> {
                showToastMessage("Unsupported fragment.")
                return
            }
        }

        supportFragmentManager.beginTransaction()
                .add(R.id.frame_fragment, fragment, fragmentTag)
                .commit()
    }

    /**
     * FragmentContainer
     */

    override fun setActivityTitle(title: String) { }

    /**
     * BaseActivity
     */

    override fun inject() {
        ActivityInjector.inject(this)
    }

    companion object {
        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.navigation"

        val ARGUMENT_FRAGMENT_TAG = "${ACTIVITY_TAG}.fragment_tag"
        val ARGUMENT_FRAGMENT_ARG = "${ACTIVITY_TAG}.fragment_argument"
        val ARGUMENT_TITLE = "${ACTIVITY_TAG}.title"

        fun launch(context: Context, fragmentTag: String, fragmentArg: Long, title: String) {
            val launcher = Intent(context, NavigationActivity::class.java)

            launcher.putExtra(ARGUMENT_FRAGMENT_TAG, fragmentTag)
            launcher.putExtra(ARGUMENT_FRAGMENT_ARG, fragmentArg)
            launcher.putExtra(ARGUMENT_TITLE, title)

            context.startActivity(launcher)
        }
    }
}