package net.sigmabeta.chipbox.view.activity

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_file_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.ActivityInjector
import net.sigmabeta.chipbox.presenter.FileListPresenter
import net.sigmabeta.chipbox.util.generateFileList
import net.sigmabeta.chipbox.view.adapter.FileAdapter
import net.sigmabeta.chipbox.view.interfaces.FileListView
import java.io.File
import javax.inject.Inject

class FileListActivity : BaseActivity(), FileListView {
    lateinit var presenter: FileListPresenter
        @Inject set

    var progressDialog: ProgressDialog? = null

    override fun inject() {
        ActivityInjector.inject(this)
    }

    public val KEY_CURRENT_PATH: String = "${BuildConfig.APPLICATION_ID}.path"

    private var adapter: FileAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_file_list)

        setSupportActionBar(toolbar_folder_list)

        // Specifying the LayoutManager determines how the RecyclerView arranges views.
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        list_files.layoutManager = layoutManager

        val path: String

        // Stuff in this block only happens when this activity is newly created (i.e. not a rotation)
        if (savedInstanceState == null) {
            path = Environment.getExternalStorageDirectory().path
        } else {
            // Get the path we were looking at before we rotated.
            path = savedInstanceState.getString(KEY_CURRENT_PATH)
        }

        adapter = FileAdapter(path, generateFileList(File(path)), this)
        list_files.adapter = adapter

        button_add_directory.setOnClickListener { onFabClick() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_file_list, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return presenter.onOptionsItemSelected(item.itemId)
                ?: super.onOptionsItemSelected(item)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Save the path we're looking at so when rotation is done, we start from same folder.
        outState.putString(KEY_CURRENT_PATH, adapter?.currentPath)
    }

    fun onFabClick() {
        val path = adapter?.currentPath

        if (path != null) {
            presenter.addDirectory(path)
        }
    }

    override fun onItemClick(path: String) {
        presenter.onItemClick(path)
    }

    override fun updateSubtitle(path: String) {
        toolbar_folder_list.subtitle = path
    }

    override fun upOneLevel() {
        adapter?.upOneLevel()
    }

    override fun setPath(path: String) {
        adapter?.setPath(path)
    }

    override fun showErrorMessage(errorId: Int) {
        Toast.makeText(this, errorId, Toast.LENGTH_SHORT).show()
    }

    override fun onAddSuccessful() {
        setResult(RESULT_ADD_SUCCESSFUL)
        finish()
    }

    override fun showExistsMessage() {
        showErrorSnackbar(
                getString(R.string.file_list_error_exists),
                View.OnClickListener { presenter.onRescanClick() },
                R.string.file_list_snackbar_rescan)
    }

    companion object {
        val REQUEST_ADD_FOLDER = 100
        val RESULT_ADD_SUCCESSFUL = 1000

        fun launch(activity: Activity) {
            val launcher = Intent(activity, FileListActivity::class.java)

            activity.startActivityForResult(launcher, REQUEST_ADD_FOLDER)
        }
    }
}
