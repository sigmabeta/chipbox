package net.sigmabeta.chipbox.presenter

import android.os.Bundle
import android.os.Environment
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.util.generateFileList
import net.sigmabeta.chipbox.util.readSingleTrackFile
import net.sigmabeta.chipbox.view.interfaces.BaseView
import net.sigmabeta.chipbox.view.interfaces.FileListView
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileListPresenter @Inject constructor(val databaseHelper: SongDatabaseHelper) : ActivityPresenter() {
    var view: FileListView? = null

    var path: String? = null

    var files: ArrayList<FileListItem>? = null

    fun onItemClick(position: Long) {
        val fileListItem = files?.get(position.toInt())
        val path = fileListItem?.path

        if (path == null) {
            view?.showErrorSnackbar("Invalid file.", null, null)
            return
        } else {
            val clickedFile = File(path)

            if (clickedFile.isDirectory) {
                val fileList = generateFileList(clickedFile)

                if (fileList.isEmpty()) {
                    view?.showErrorMessage(R.string.file_list_error_empty_folder)
                } else {
                    loadFolder(clickedFile)
                }
            } else {
                val track = readSingleTrackFile(path, 0)

                if (track != null) {
                    // TODO Show human-readable details.
                    view?.showToastMessage(track.toString())
                } else {
                    view?.showErrorMessage(R.string.file_list_error_invalid_track)
                }
            }
        }
    }

    fun onOptionsItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_up_one_level -> upOneLevel()
        }

        return true
    }

    fun onFabClick() {
        path?.let {
            val subscription = databaseHelper.addDirectory(it)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            {
                                when (it) {
                                    SongDatabaseHelper.ADD_STATUS_GOOD -> view?.onAddSuccessful()
                                    SongDatabaseHelper.ADD_STATUS_EXISTS -> view?.showExistsMessage()
                                    SongDatabaseHelper.ADD_STATUS_DB_ERROR -> view?.showErrorMessage(R.string.file_list_error_adding)
                                }
                            },
                            {
                                view?.showErrorMessage(R.string.file_list_error_adding)
                            }
                    )

            subscriptions.add(subscription)
        }
    }

    fun onRescanClick() {
        view?.onAddSuccessful()
    }

    override fun onReCreate(savedInstanceState: Bundle) {
    }

    override fun onTempDestroy() {
    }

    override fun setup(arguments: Bundle?) {
        path = Environment.getExternalStorageDirectory().path

        val folder = File(path)
        files = generateFileList(folder)
    }

    override fun teardown() {
        path = null
    }

    override fun updateViewState() {
        files?.let {
            view?.setFiles(it)
        }
    }

    override fun setView(view: BaseView) {
        if (view is FileListView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    private fun upOneLevel() {
        val currentDirectory = File(path)
        val parentDirectory = currentDirectory.parentFile

        loadFolder(parentDirectory)
    }

    private fun loadFolder(clickedFile: File) {
        val files = generateFileList(clickedFile)

        this.files = files
        this.path = clickedFile.path

        view?.setFiles(files)
    }
}