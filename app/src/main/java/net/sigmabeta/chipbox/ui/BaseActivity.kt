package net.sigmabeta.chipbox.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.squareup.picasso.Callback
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.className


abstract class BaseActivity<out P : ActivityPresenter<in V>, in V : BaseView> : AppCompatActivity(), BaseView, View.OnClickListener {
    var lastPermRequestId: String? = null
    var lastPermRequestAction: (() -> Unit)? = null

    override fun showErrorState() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun showEmptyState() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun getPicassoCallback(): Callback {
        return object : Callback {
            override fun onSuccess() {
                getPresenterImpl().onImageLoadSuccess()
                startPostponedEnterTransition()
            }

            override fun onError() {
                getPresenterImpl().onImageLoadError()
                startPostponedEnterTransition()
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

    override fun requestReSetup() {
        getPresenterImpl().onCreate(intent.extras, null, this as V)
    }

    /**
     * Requests an injection of the Activity's dependencies (usually, a
     * Presenter) then does the usual Activity setup (superclass implementation,
     * setContentView, setup of transitions, etc.)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        inject()

        if (savedInstanceState != null && !isChangingConfigurations) {
            getPresenterImpl().view = this as V
        }

        super.onCreate(savedInstanceState)

        setContentView(getLayoutId())
        inflateContent()
        setTransitions()
        configureViews()

        getPresenterImpl().onCreate(intent.extras, savedInstanceState, this as V)

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
        getPresenterImpl().onReenter()
    }

    override fun onResume() {
        super.onResume()
        getPresenterImpl().onResume(this as V)
    }

    override fun onPause() {
        super.onPause()
        getPresenterImpl().onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        getPresenterImpl().onDestroy(isFinishing, this as V)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (getTypedApplication().shouldShowDetailedErrors()) {
            showSnackbar("Memory low.", null, 0)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (getTypedApplication().shouldShowDetailedErrors()) {
//            showSnackbar("Trimming memory.", null, 0)
        }
    }

    override fun onClick(clicked: View) {
        getPresenterImpl().onClick(clicked.id)
    }

    override fun showError(message: String, action: View.OnClickListener?, actionLabel: Int) {
        if (getTypedApplication().shouldShowDetailedErrors()) {
            showSnackbar(message, action, actionLabel)
        } else {
            showSnackbar("An error occurred. Please try again.", action, actionLabel)
        }
    }

    fun getFragmentComponent() = getPresenterImpl().fragmentComponent

    fun getShareableNavBar(): Pair<View, String>? {
        return Pair(window.decorView.findViewById(android.R.id.navigationBarBackground) ?: return null,
                Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME)
    }

    fun getShareableStatusBar(): Pair<View, String>? {
        return Pair(window.decorView.findViewById(android.R.id.statusBarBackground) ?: return null,
                Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME)
    }

    protected open fun inflateContent() {}

    protected open fun setTransitions() = Unit

    protected fun showSnackbar(message: String, action: View.OnClickListener?, actionLabel: Int?) {
        val snackbar = Snackbar.make(getContentLayout(), message, Snackbar.LENGTH_LONG)

        if (action != null && actionLabel != null) {
            snackbar.setAction(actionLabel, action)
        }

        snackbar.show()
    }

    protected fun showSnackbarPermanent(message: String, action: View.OnClickListener?, actionLabel: Int?) {
        val snackbar = Snackbar.make(getContentLayout(), message, Snackbar.LENGTH_INDEFINITE)

        if (action != null && actionLabel != null) {
            snackbar.setAction(actionLabel, action)
        } else {
            snackbar.setAction(R.string.error_cta_dismiss) { snackbar.dismiss() }
        }

        snackbar.show()
    }

    override fun getTypedApplication() = application as ChipboxApplication

    /**
     * Must be overridden to request the activity's dependencies
     * and do any other necessary setup.
     */
    protected abstract fun inject()

    protected abstract fun getPresenterImpl(): P

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

        showSnackbar(message,
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
        showSnackbar(getString(R.string.permission_general_permanent), null, 0)
    }

    protected fun setupToolbar(showArrow: Boolean) {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        if (toolbar != null) {
            setSupportActionBar(toolbar)

            val actionBar = supportActionBar
            actionBar?.setDisplayHomeAsUpEnabled(true)
        }

        if (showArrow) showBackXInToolbar() else showBackXInToolbar()
    }

    protected open fun showBackArrowInToolbar() {
        findViewById<Toolbar>(R.id.toolbar)?.setNavigationOnClickListener { v -> onBackPressed() }
    }

    protected fun showBackXInToolbar() {
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_clear_white_24dp)
        findViewById<Toolbar>(R.id.toolbar)?.setNavigationOnClickListener { v -> onBackPressed() }
    }

    companion object {
        val ACTIVITY_ARGUMENTS = "${BuildConfig.APPLICATION_ID}.${className()}.arguments"
    }
}