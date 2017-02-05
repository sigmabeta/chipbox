package net.sigmabeta.chipbox.ui.scan

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.activity_scan.*
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.util.changeText
import javax.inject.Inject

class ScanActivity : BaseActivity<ScanPresenter, ScanView>(), ScanView {
    lateinit var presenter: ScanPresenter
        @Inject set

    override fun onBackPressed() {
        presenter.onBackPressed()
    }

    /**
     * ScanView
     */

    override fun onScanFailed() {
        text_status.changeText(getString(R.string.scan_status_failed))
    }

    override fun onScanComplete(refresh: Boolean) {
        setResult(if (refresh) RESULT_CODE_REFRESH else RESULT_CODE_NO_REFRESH)
        text_status.changeText(getString(R.string.scan_status_complete))
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
        getTypedApplication().appComponent.inject(this)
    }

    override fun showLoading() = Unit

    override fun hideLoading() = Unit

    override fun getPresenterImpl() = presenter

    override fun configureViews() {
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_scan
    }

    override fun getContentLayout(): FrameLayout {
        return findViewById(android.R.id.content) as FrameLayout
    }

    override fun getSharedImage(): View? = null

    override fun shouldDelayTransitionForFragment() = false

    companion object {
        val REQUEST_CODE_SCAN = 123

        val RESULT_CODE_REFRESH = 789
        val RESULT_CODE_NO_REFRESH = 456

        fun launch(activity: Activity) {
            val launcher = Intent(activity, ScanActivity::class.java)
            activity.startActivityForResult(launcher, REQUEST_CODE_SCAN)
        }
    }
}