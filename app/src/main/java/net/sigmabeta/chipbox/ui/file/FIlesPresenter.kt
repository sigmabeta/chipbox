package net.sigmabeta.chipbox.ui.file

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.database.Library
import net.sigmabeta.chipbox.model.file.Folder
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.util.logError
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilesPresenter @Inject constructor() : ActivityPresenter() {
    // TODO DI this
    lateinit var startPath: String

    var view: FilesView? = null

    var path: String? = null

    fun onOptionsItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_up_one_level -> upOneLevel()
        }

        return true
    }

    fun onDirectoryClicked(path: String) {
        this.path = path
        view?.showFileFragment(path, true)
    }

    fun onFabClick() {
        path?.let {
            val subscription = Folder.addToDatabase(it)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            {
                                when (it) {
                                    Library.ADD_STATUS_GOOD -> view?.onAddSuccessful()
                                    Library.ADD_STATUS_EXISTS -> view?.showExistsMessage()
                                    Library.ADD_STATUS_DB_ERROR -> view?.showErrorMessage(R.string.file_list_error_adding)
                                }
                            },
                            {
                                logError("[FilesPresenter] File add error: ${it.message}")
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
        val path = arguments?.getString(FilesActivity.ARGUMENT_PATH)

        if (path != null) {
            this.path = path

            startPath = path
            view?.showFileFragment(path, false)
        }
    }

    override fun teardown() {
        path = null
    }

    override fun updateViewState() {
    }

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is FilesView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    override fun onReenter() = Unit

    private fun upOneLevel() {
        var popStack = false

        path?.let {
            if (it.contains(startPath) && it != startPath) {
                popStack = true
            }
        }

        val parentPath = File(path).parentFile.path
        this.path = parentPath

        if (popStack) {
            view?.popBackStack()
        } else {
            view?.showFileFragment(parentPath, true)
        }
    }
}