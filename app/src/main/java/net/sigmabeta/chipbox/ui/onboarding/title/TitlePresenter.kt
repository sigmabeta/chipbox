package net.sigmabeta.chipbox.ui.onboarding.title

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.FragmentPresenter
import javax.inject.Inject


@ActivityScoped
class TitlePresenter @Inject constructor(): FragmentPresenter() {

    var view: TitleView? = null

    /**
     * Public Methods
     */



    /**
     * FragmentPresenter
     */

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_next -> view?.onNextClicked()
            R.id.button_skip -> view?.onSkipClicked()
        }
    }

    /**
     * BasePresenter
     */

    override fun setup(arguments: Bundle?) = Unit

    override fun teardown() = Unit

    override fun updateViewState() = Unit

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is TitleView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    /**
     * Private Methods
     */


}