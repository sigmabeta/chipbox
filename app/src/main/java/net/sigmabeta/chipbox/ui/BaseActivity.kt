package net.sigmabeta.chipbox.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import com.squareup.picasso.Callback
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.component.FragmentComponent
import net.sigmabeta.chipbox.util.*


abstract class BaseActivity : AppCompatActivity(), BaseView, View.OnClickListener {
    var lastPermRequestId: String? = null
    var lastPermRequestAction: (() -> Unit)? = null

    fun getPicassoCallback(): Callback {
        return object : Callback {
            override fun onSuccess() {
                startPostponedEnterTransition()
            }

            override fun onError() {
                startPostponedEnterTransition()
                logError("[BaseActivity] Couldn't load image.")
            }
        }
    }

    fun doWithPermission(permissionId: String, action: () -> Unit) {
        // If we don't have permission to do the thing yet
        if (ContextCompat.checkSelfPermission(this, permissionId) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissionId)) {
                showPermissionExplanation(permissionId, action)
            } else {
                requestPermission(permissionId, action)
            }
        } else {
            lastPermRequestId = null
            lastPermRequestAction = null
            // Do the thing
            action.invoke()
        }
    }

    /**
     * Requests an injection of the Activity's dependencies (usually, a
     * Presenter) then does the usual Activity setup (superclass implementation,
     * setContentView, setup of transitions, etc.)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        inject()
        super.onCreate(savedInstanceState)

        setContentView(getLayoutId())

        window.enterTransition = TRANSITION_FADE_IN_BELOW
        window.reenterTransition = TRANSITION_STAGGERED_FADE_IN_ABOVE
        window.exitTransition = TRANSITION_STAGGERED_FADE_OUT_UP
        window.returnTransition = TRANSITION_FADE_OUT_DOWN

        configureViews()
        getPresenter().onCreate(intent.extras, savedInstanceState, this)

        val sharedView = getSharedImage()

        if (sharedView != null) {
            setResult(RESULT_OK)
            if (sharedView is ImageView) {
                postponeEnterTransition()
            }
        } else if (shouldDelayTransitionForFragment()) {
            setResult(RESULT_OK)
            postponeEnterTransition()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            (lastPermRequestId?.hashCode() ?: 0) and 0xFFFF -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    lastPermRequestAction?.invoke()
                } else {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                        // User may have accidentally clicked no.
                        showPermissionExplanation(lastPermRequestId!!, lastPermRequestAction!!)
                    } else {
                        // User clicked "don't ask again."
                        showPermanentDenialError(lastPermRequestId!!)
                    }
                }
                lastPermRequestId = null
                lastPermRequestAction = null

                return
            }

            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        super.onActivityReenter(resultCode, data)
        getPresenter().onReenter()
    }

    override fun onResume() {
        super.onResume()
        getPresenter().onResume()
    }

    override fun onPause() {
        super.onPause()
        getPresenter().onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        getPresenter().onDestroy(isFinishing)
    }

    override fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showErrorSnackbar(message: String, action: View.OnClickListener?, actionLabel: Int?) {
        val snackbar = Snackbar.make(getContentLayout(), message, Snackbar.LENGTH_LONG)

        if (action != null && actionLabel != null) {
            snackbar.setAction(actionLabel, action)
        }

        snackbar.show()
    }

    override fun onClick(clicked: View) {
        getPresenter().onClick(clicked.id)
    }

    fun getFragmentComponent(): FragmentComponent {
        return getPresenter().fragmentComponent
    }

    open fun getShareableViews(): Array<Pair<View, String>>? = null

    /**
     * Must be overridden to request the activity's dependencies
     * and do any other necessary setup.
     */
    protected abstract fun inject()

    protected abstract fun getPresenter(): ActivityPresenter

    /**
     * Perform any necessary run-time setup of views: RecyclerViews, ClickListeners,
     * etc.
     */
    protected abstract fun configureViews()

    protected abstract fun getLayoutId(): Int

    protected abstract fun getContentLayout(): ViewGroup

    protected abstract fun getSharedImage(): View?

    /**
     * Return true here if the Activity contains a Fragment with a SharedElement view inside
     * it for whose readiness we must wait before beginning the transition. If this Activity's
     * getSharedImage() call returns non-null, this is ignored.
     */
    protected abstract fun shouldDelayTransitionForFragment(): Boolean

    protected fun showPermissionExplanation(permissionId: String, action: () -> Unit) {
        val message = if (permissionId == Manifest.permission.READ_EXTERNAL_STORAGE) {
            getString(R.string.permission_storage_explanation)
        } else {
            getString(R.string.permission_general_explanation)
        }

        showErrorSnackbar(message,
                View.OnClickListener { requestPermission(permissionId, action) },
                R.string.permission_cta)
    }

    protected fun requestPermission(permissionId: String, action: () -> Unit) {
        lastPermRequestId = permissionId
        lastPermRequestAction = action

        ActivityCompat.requestPermissions(this,
                arrayOf<String>(permissionId),
                permissionId.hashCode() and 0xFFFF)
    }

    private fun showPermanentDenialError(lastPermRequestId: String) {
        showErrorSnackbar(getString(R.string.permission_general_permanent), null, 0)
    }
}