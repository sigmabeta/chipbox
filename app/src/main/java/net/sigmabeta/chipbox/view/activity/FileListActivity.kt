package net.sigmabeta.chipbox.view.activity

import android.app.Activity
import android.content.Intent
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_file_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.presenter.ActivityPresenter
import net.sigmabeta.chipbox.presenter.FileListPresenter
import net.sigmabeta.chipbox.view.adapter.FileAdapter
import net.sigmabeta.chipbox.view.interfaces.FileListView
import net.sigmabeta.chipbox.view.interfaces.ItemListView
import java.util.*
import javax.inject.Inject

class FileListActivity : BaseActivity(), FileListView, ItemListView {
    lateinit var presenter: FileListPresenter
        @Inject set

    var adapter = FileAdapter(this)

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

    override fun setFiles(files: ArrayList<FileListItem>) {
        adapter.dataset = files
    }

    override fun updateSubtitle(path: String) {
        toolbar_folder_list.subtitle = path
    }

    override fun showErrorMessage(errorId: Int) {
        Toast.makeText(this, errorId, Toast.LENGTH_SHORT).show()
    }

    override fun onAddSuccessful() {
        setResult(RESULT_ADD_SUCCESSFUL)
        finish()
    }

    override fun onItemClick(position: Long) {
        presenter.onItemClick(position)
    }

    override fun showExistsMessage() {
        showErrorSnackbar(
                getString(R.string.file_list_error_exists),
                View.OnClickListener { presenter.onRescanClick() },
                R.string.file_list_snackbar_rescan)
    }

    override fun inject() {
        ChipboxApplication.appComponent.inject(this)
    }

    override fun getPresenter(): ActivityPresenter {
        return presenter
    }

    override fun configureViews() {
        setSupportActionBar(toolbar_folder_list)

        // Specifying the LayoutManager determines how the RecyclerView arranges views.
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        list_files.layoutManager = layoutManager
        list_files.adapter = adapter

        button_add_directory.setOnClickListener { onFabClick() }
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_file_list
    }

    override fun getContentLayout(): FrameLayout {
        return frame_content
    }

    companion object {
        val REQUEST_ADD_FOLDER = 100
        val RESULT_ADD_SUCCESSFUL = 1000

        val KEY_CURRENT_PATH: String = "${BuildConfig.APPLICATION_ID}.path"

        fun launch(activity: Activity) {
            val launcher = Intent(activity, FileListActivity::class.java)

            activity.startActivityForResult(launcher, REQUEST_ADD_FOLDER)
        }
    }
}
