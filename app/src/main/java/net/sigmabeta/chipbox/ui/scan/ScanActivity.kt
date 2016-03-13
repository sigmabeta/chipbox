package net.sigmabeta.chipbox.ui.scan

import android.content.Context
import android.content.Intent
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.activity_scan.*
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.scan.ScanPresenter
import net.sigmabeta.chipbox.util.fadeInFromRight
import net.sigmabeta.chipbox.util.fadeOutToLeft
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.scan.ScanView
import javax.inject.Inject

class ScanActivity : BaseActivity(), ScanView {
    lateinit var presenter: ScanPresenter
        @Inject set

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
        ChipboxApplication.appComponent.inject(this)
    }

    override fun getPresenter(): ActivityPresenter {
        return presenter
    }

    override fun configureViews() {
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_scan
    }

    override fun getContentLayout(): FrameLayout {
        return findViewById(android.R.id.content) as FrameLayout
    }

    companion object {
        fun launch(context: Context) {
            val launcher = Intent(context, ScanActivity::class.java)
            context.startActivity(launcher)
        }
    }
}