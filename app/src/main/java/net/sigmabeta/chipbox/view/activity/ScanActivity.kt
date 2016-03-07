package net.sigmabeta.chipbox.view.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_scan.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.ActivityInjector
import net.sigmabeta.chipbox.presenter.ScanPresenter
import net.sigmabeta.chipbox.util.fadeInFromRight
import net.sigmabeta.chipbox.util.fadeOutToLeft
import net.sigmabeta.chipbox.view.interfaces.ScanView
import javax.inject.Inject

class ScanActivity : BaseActivity(), ScanView {
    lateinit var presenter: ScanPresenter
        @Inject set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_scan)

        presenter.onCreate()
    }

    override fun onBackPressed() {
        presenter.onBackPressed()
    }

    /**
     * ScanView
     */

    override fun onScanFailed() {
        text_status.fadeOutToLeft().withEndAction {
            text_status.setText(R.string.scan_status_failed)
            text_status.fadeInFromRight()
        }
    }

    override fun onScanComplete() {
        text_status.fadeOutToLeft().withEndAction {
            text_status.setText(R.string.scan_status_complete)
            text_status.fadeInFromRight()
        }
    }

    override fun showCurrentFolder(name: String) {
        text_current_folder.text = name
    }

    override fun showLastFile(name: String) {
        text_last_file.text = name
    }

    override fun updateFilesAdded(filesAdded: Int) {
        text_file_added_count.text = filesAdded.toString()
    }

    override fun updateBadFiles(badFiles: Int) {
        text_bad_file_count.text = badFiles.toString()
    }

    /**
     * BaseActivity
     */

    override fun inject() {
        ActivityInjector.inject(this)
    }

    companion object {
        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.Scan"

        val ARGUMENT_ID = "${ACTIVITY_TAG}.id"

        fun launch(context: Context) {
            val launcher = Intent(context, ScanActivity::class.java)

            context.startActivity(launcher)
        }
    }
}