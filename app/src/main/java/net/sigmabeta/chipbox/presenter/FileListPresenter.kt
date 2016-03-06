package net.sigmabeta.chipbox.presenter

import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.util.generateFileList
import net.sigmabeta.chipbox.util.readTrackInfoFromPath
import net.sigmabeta.chipbox.view.interfaces.FileListView
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ActivityScoped
class FileListPresenter @Inject constructor(val view: FileListView,
                                            val databaseHelper: SongDatabaseHelper) {
    fun onOptionsItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_up_one_level -> view.upOneLevel()
        }

        return true
    }

    fun onItemClick(path: String) {
        val clickedFile = File(path)

        if (clickedFile.isDirectory) {
            val fileList = generateFileList(clickedFile)

            if (fileList.isEmpty()) {
                view.showErrorMessage(R.string.file_list_error_empty_folder)
            } else {
                Observable.just(view.setPath(path))
                        .delaySubscription(400, TimeUnit.MILLISECONDS)
                        .subscribe()
            }
        } else {
            val track = readTrackInfoFromPath(path, 0)

            if (track != null) {
                // TODO Show human-readable details.
                view.showToastMessage(track.toString())
            } else {
                view.showErrorMessage(R.string.file_list_error_invalid_track)
            }
        }
    }

    fun addDirectory(path: String) {
        databaseHelper.addDirectory(path)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            when (it) {
                                SongDatabaseHelper.ADD_STATUS_GOOD -> view.startScanActivity()
                                SongDatabaseHelper.ADD_STATUS_EXISTS -> view.showExistsMessage()
                                SongDatabaseHelper.ADD_STATUS_DB_ERROR -> view.showErrorMessage(R.string.file_list_error_adding)
                            }
                        },
                        {
                            view.showErrorMessage(R.string.file_list_error_adding)
                        }
                )
    }

    fun onRescanClick() {
        view.startScanActivity()
    }
}