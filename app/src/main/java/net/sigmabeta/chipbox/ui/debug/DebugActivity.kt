package net.sigmabeta.chipbox.ui.debug

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_debug.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.util.changeText
import javax.inject.Inject

class DebugActivity : BaseActivity<DebugPresenter, DebugView>(),
        DebugView, AdapterView.OnItemSelectedListener {
    lateinit var presenter: DebugPresenter
        @Inject set

    /**
     * OnItemSelectedListener
     */

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        when ((view?.parent as View).id) {
            R.id.spinner_buffer_bytes -> presenter.onBufferMultiplierChange(position + 1)
            R.id.spinner_buffer_count -> presenter.onBufferCountChange(position + 1)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit

    /**
     * DebugView
     */

    override fun showBufferSize(minBytes: Int, multiplier: Int) {
        if (spinner_buffer_bytes.adapter == null) {
            // Generate an array of all multiples of `minBytes` from 1 to 20.
            val possibleByteValues = Array<Int>(20) {
                (it + 1) * minBytes
            }

            val bufferSizeAdapter = ArrayAdapter(this, R.layout.dropdown_before_click_tempo, possibleByteValues)
            bufferSizeAdapter.setDropDownViewResource(R.layout.dropdown_after_click_tempo)

            spinner_buffer_bytes.adapter = bufferSizeAdapter
        }

        spinner_buffer_bytes.post {
            spinner_buffer_bytes.setSelection(multiplier - 1)
            spinner_buffer_bytes.onItemSelectedListener = this
        }
    }

    override fun showBufferCount(buffers: Int) {
        if (spinner_buffer_count.adapter == null) {
            val bufferCountRange = 1..20
            val bufferCountAdapter = ArrayAdapter(this, R.layout.dropdown_before_click_tempo, bufferCountRange.toList())
            bufferCountAdapter.setDropDownViewResource(R.layout.dropdown_after_click_tempo)

            spinner_buffer_count.adapter = bufferCountAdapter
        }

        spinner_buffer_count.post {
            spinner_buffer_count.setSelection(buffers - 1)
            spinner_buffer_count.onItemSelectedListener = this
        }
    }

    override fun showMinimumLatency(millis: Int) {
        text_buffer_latency_minimum.changeText(getString(R.string.debug_value_latency, millis))
    }

    override fun showActualLatency(millis: Int) {
        text_buffer_latency_actual.changeText(getString(R.string.debug_value_latency, millis))
    }

    override fun showTotalBufferSize(millis: Int) {
        text_buffer_latency_total.changeText(getString(R.string.debug_value_latency, millis))
    }

    override fun showSampleRate(hertz: Int) {
        text_sample_rate.text = getString(R.string.debug_value_sample_rate, hertz)
    }

    /**
     * BaseActivity
     */

    override fun showLoading() = Unit

    override fun hideLoading() = Unit

    override fun inject() = getTypedApplication().appComponent.inject(this)

    override fun getPresenterImpl() = presenter

    override fun configureViews() = Unit

    override fun getLayoutId() = R.layout.activity_debug

    override fun getContentLayout() = linear_content

    override fun getSharedImage() = null

    override fun shouldDelayTransitionForFragment() = false

    companion object {
        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.debug"

        fun launch(context: Context) {
            val launcher = Intent(context, DebugActivity::class.java)
            context.startActivity(launcher)
        }
    }
}