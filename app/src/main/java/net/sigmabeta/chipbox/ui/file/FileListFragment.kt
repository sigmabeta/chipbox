package net.sigmabeta.chipbox.ui.file

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.fragment_file_list.*
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.file.FileListItem
import net.sigmabeta.chipbox.ui.BaseActivity
import net.sigmabeta.chipbox.ui.BaseFragment
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.ui.ItemListView
import net.sigmabeta.chipbox.util.TRANSITION_FADE_IN_BELOW
import net.sigmabeta.chipbox.util.TRANSITION_FADE_OUT_DOWN
import net.sigmabeta.chipbox.util.TRANSITION_STAGGERED_FADE_IN_ABOVE
import net.sigmabeta.chipbox.util.TRANSITION_STAGGERED_FADE_OUT_UP
import java.util.*
import javax.inject.Inject

class FileListFragment : BaseFragment(), FileListView, ItemListView<FileViewHolder> {
    lateinit var presenter: FileListPresenter
        @Inject set

    var adapter = FileListAdapter(this)

    /**
     * FileListView
     */

    override fun setFiles(files: ArrayList<FileListItem>) {
        adapter.dataset = files
    }

    override fun onDirectoryClicked(path: String) {
        (activity as FilesView).onDirectoryClicked(path)
    }

    override fun onInvalidTrackClicked() {
        showErrorSnackbar(getString(R.string.file_list_error_invalid_track),
                null,
                null)
    }

    /**
     * ItemListView
     */

    override fun onItemClick(position: Long, clickedViewHolder: FileViewHolder) {
        presenter.onItemClick(position)
    }

    /**
     * BaseFragment
     */

    override fun inject() {
        val container = activity
        if (container is BaseActivity) {
            container.getFragmentComponent().inject(this)
        }
    }

    override fun getPresenter(): FragmentPresenter {
        return presenter
    }

    override fun getLayoutId(): Int {
        return R.layout.fragment_file_list
    }

    override fun getContentLayout(): FrameLayout {
        return frame_content
    }

    override fun configureViews() {
        // Specifying the LayoutManager determines how the RecyclerView arranges views.
        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)

        list_files.layoutManager = layoutManager
        list_files.adapter = adapter
    }

    override fun getSharedImage(): View? = null

    companion object {
        val FRAGMENT_TAG = "${BuildConfig.APPLICATION_ID}.file_list"

        val ARGUMENT_PATH = "${FRAGMENT_TAG}.path"

        fun newInstance(path: String): FileListFragment {
            val fragment = FileListFragment()

            val arguments = Bundle()
            arguments.putString(ARGUMENT_PATH, path)

            fragment.arguments = arguments

            fragment.enterTransition = TRANSITION_FADE_IN_BELOW
            fragment.returnTransition = TRANSITION_FADE_OUT_DOWN
            fragment.reenterTransition = TRANSITION_STAGGERED_FADE_IN_ABOVE
            fragment.exitTransition = TRANSITION_STAGGERED_FADE_OUT_UP

            return fragment
        }
    }
}