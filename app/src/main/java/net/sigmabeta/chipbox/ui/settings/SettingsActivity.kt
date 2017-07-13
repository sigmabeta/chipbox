package net.sigmabeta.chipbox.ui.settings

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_settings.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.ItemListView
import javax.inject.Inject

class SettingsActivity : BaseActivity<SettingsPresenter, SettingsView>(), SettingsView, ItemListView<VoiceViewHolder>, AdapterView.OnItemSelectedListener {
    lateinit var presenter: SettingsPresenter
        @Inject set

    var adapter = VoicesAdapter(this)

    /**
     * SettingsView
     */

    override fun notifyChanged(position: Int) {
        adapter.notifyItemChanged(position)
    }

    override fun setVoices(voices: MutableList<Voice>?) {
        adapter.dataset = voices
    }

    override fun setDropdownValue(index: Int) {
        dropdown_tempo.onItemSelectedListener = null
        dropdown_tempo.post {
            dropdown_tempo.setSelection(index)
            dropdown_tempo.onItemSelectedListener = this
        }
    }

    /**
     * ItemListView
     */

    override fun onItemClick(position: Int) {
        presenter.onItemClick(position)
    }

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

    override fun showLoading() = Unit

    override fun hideLoading() = Unit

    override fun inject() = getTypedApplication().appComponent.inject(this)

    override fun getPresenterImpl() = presenter

    override fun configureViews() {
        recycler_voices.adapter = adapter

        val spinAdapter = ArrayAdapter.createFromResource(this, R.array.display_tempo, R.layout.dropdown_before_click_tempo)
        spinAdapter.setDropDownViewResource(R.layout.dropdown_after_click_tempo)

        dropdown_tempo.adapter = spinAdapter
        dropdown_tempo.onItemSelectedListener = this
    }

    override fun getLayoutId() = R.layout.activity_settings

    override fun getContentLayout() = linear_content

    override fun getSharedImage() = null

    override fun shouldDelayTransitionForFragment() = false

    companion object {
        fun launch(context: Context) {
            val launcher = Intent(context, SettingsActivity::class.java)
            context.startActivity(launcher)
        }
    }
}
