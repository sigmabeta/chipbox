package net.sigmabeta.chipbox.ui.file

import android.app.Activity
import android.content.Intent
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_files.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.FragmentContainer
import net.sigmabeta.chipbox.ui.scan.ScanActivity
import javax.inject.Inject

class FilesActivity : BaseActivity(), FilesView, FragmentContainer {
    lateinit var presenter: RenamedPresenter
        @Inject set

    override fun showFileFragment(path: String, stack: Boolean) {
        var fragment = FileListFragment.newInstance(path)

        val transaction = supportFragmentManager.beginTransaction()

        if (stack) {
            transaction.addToBackStack(null)
        }

        transaction.replace(R.id.frame_fragment, fragment, FileListFragment.FRAGMENT_TAG + "." + path)
        transaction.commit()
    }

    override fun popBackStack() {
        supportFragmentManager.popBackStack()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_file_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return presenter.onOptionsItemSelected(item.itemId)
    }

    fun onFabClick() {
        presenter.onFabClick()
    }

    override fun updateSubtitle(path: String) {
        actionBar?.subtitle = path
    }

    override fun showErrorMessage(errorId: Int) {
        Toast.makeText(this, errorId, Toast.LENGTH_SHORT).show()
    }

    override fun onAddSuccessful() {

        ScanActivity.launch(this)
    }

    override fun onDirectoryClicked(path: String) {
        presenter.onDirectoryClicked(path)
    }

    override fun showExistsMessage() {
        showErrorSnackbar(
                getString(R.string.file_list_error_exists),
                this,
                R.string.file_list_snackbar_rescan)
    }

    override fun setTitle(title: String) {
        this.title = title
    }

    override fun inject() {
        ChipboxApplication.appComponent.inject(this)
    }

    override fun getPresenter(): ActivityPresenter {
        return presenter
    }

    override fun configureViews() {
        button_add_directory.setOnClickListener { onFabClick() }
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_files
    }

    override fun getContentLayout(): FrameLayout {
        return frame_content
    }

    override fun getSharedImage(): View? = null

    override fun shouldDelayTransitionForFragment() = false

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ScanActivity.REQUEST_CODE_SCAN) {
            if (resultCode == ScanActivity.RESULT_CODE_REFRESH) {
                setResult(resultCode)
                finish()
            }
        }
    }

    companion object {
        val REQUEST_ADD_FOLDER = 100

        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.files"

        val ARGUMENT_PATH: String = "${ACTIVITY_TAG}.path"

        fun launch(activity: Activity) {
            val launcher = Intent(activity, FilesActivity::class.java)

            launcher.putExtra(ARGUMENT_PATH, Environment.getExternalStorageDirectory().path)

            activity.startActivityForResult(launcher, REQUEST_ADD_FOLDER)
        }
    }
}
