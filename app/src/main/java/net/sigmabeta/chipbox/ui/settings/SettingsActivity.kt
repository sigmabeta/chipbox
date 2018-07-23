package net.sigmabeta.chipbox.ui.settings

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import io.realm.OrderedCollectionChangeSet
import kotlinx.android.synthetic.main.activity_settings.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.ui.BaseActivity
import javax.inject.Inject

class SettingsActivity : BaseActivity<SettingsPresenter, SettingsView>(), SettingsView, AdapterView.OnItemSelectedListener {
    lateinit var presenter: SettingsPresenter
        @Inject set

    var adapter = VoicesAdapter(this)

    /**
     * SettingsView
     */

    override fun notifyChanged(position: Int) {
        adapter.notifyItemChanged(position)
    }

    override fun setList(list: List<Voice>) {
        adapter.dataset = list
    }

    override fun setDropdownValue(index: Int) {
        dropdown_tempo.onItemSelectedListener = null
        dropdown_tempo.setSelection(index, false)
        dropdown_tempo.onItemSelectedListener = this
    }

    override fun animateChanges(changeset: OrderedCollectionChangeSet) = Unit

    override fun refreshList() = Unit

    /**
     * ListView
     */

    override fun onItemClick(position: Int) {
        presenter.onItemClick(position)
    }

    override fun isScrolledToBottom(): Boolean = true

    override fun startRescan() = Unit

    override fun showScanningWaitMessage() = Unit

    /**
     * OnItemSelectedListener
     */

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        presenter.onTempoChange(position)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit

    /**
     * BaseActivity
     */

    override fun showLoadingState() = Unit

    override fun showContent() = Unit

    override fun inject() = getTypedApplication().appComponent.inject(this)

    override fun getPresenterImpl() = presenter

    override fun configureViews() {
        recycler_voices.adapter = adapter

        val spinAdapter = ArrayAdapter.createFromResource(this, R.array.display_tempo, R.layout.dropdown_before_click_tempo)
        spinAdapter.setDropDownViewResource(R.layout.dropdown_after_click_tempo)

        dropdown_tempo.adapter = spinAdapter

        setupToolbar(false)
    }

    override fun getLayoutId() = R.layout.activity_settings

    override fun getContentLayout() = layout_background

    override fun getSharedImage() = null

    override fun shouldDelayTransitionForFragment() = false

    companion object {
        fun launch(context: Context) {
            val launcher = Intent(context, SettingsActivity::class.java)
            context.startActivity(launcher)
        }
    }
}
