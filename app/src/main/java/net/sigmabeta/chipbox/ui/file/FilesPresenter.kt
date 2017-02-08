package net.sigmabeta.chipbox.ui.file

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.module.AppModule
import net.sigmabeta.chipbox.ui.ActivityPresenter
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class FilesPresenter @Inject constructor(@Named(AppModule.DEP_NAME_BROWSER_START) val startPath: String) : ActivityPresenter<FilesView>() {
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
            if (path == "/") {
                view?.showNoAddingRootError()
                return
            }
        }
    }

    fun onRescanClick() {
        view?.onAddSuccessful()
    }

    fun onNotFolderError() {
        upOneLevel()

    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun onTempDestroy() = Unit

    override fun setup(arguments: Bundle?) {
        needsSetup = false

        path = startPath
        view?.showFileFragment(startPath, false)
    }

    override fun teardown() {
        path = null
    }

    override fun updateViewState() = Unit

    override fun onClick(id: Int) {
        when (id) {
            android.support.design.R.id.snackbar_action -> onRescanClick()
        }
    }

    override fun onReenter() = Unit

    private fun upOneLevel() {
        var popStack = false

        path?.let {
            if (it.contains(startPath) && it != startPath) {
                popStack = true
            }
        }

        val parentFile = File(path).parentFile
        if (parentFile != null) {
            val parentPath = parentFile.path
            this.path = parentPath

            if (popStack) {
                view?.popBackStack()
            } else {
                view?.showFileFragment(parentPath, true)
            }
        } else {
            view?.showErrorMessage(R.string.file_list_error_no_parent)
        }
    }
}