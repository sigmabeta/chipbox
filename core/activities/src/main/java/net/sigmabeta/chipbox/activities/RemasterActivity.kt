package net.sigmabeta.chipbox.activities

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.MaterialTheme
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.accompanist.insets.ProvideWindowInsets
import dagger.hilt.android.AndroidEntryPoint
import net.sigmabeta.chipbox.services.ChipboxPlaybackService
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RemasterActivity : ComponentActivity() {
    @Inject
    lateinit var topViewModel: TopViewModel

    lateinit var permissionLauncher: ActivityResultLauncher<String>

    private lateinit var mediaBrowser: MediaBrowserCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ChipboxImmersive)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        val displayMetrics = resources.displayMetrics
        val widthPixels = displayMetrics.widthPixels
        val heightPixels = displayMetrics.heightPixels

        // TODO Don't request on launch
        setupPermissions()

        Timber.v("Device screen DPI: ${displayMetrics.densityDpi}")
        Timber.v("Device screen scaling factor: ${displayMetrics.density}")
        Timber.v("Device screen size: ${widthPixels}x$heightPixels")

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, ChipboxPlaybackService::class.java),
            connectionCallbacks,
            null // optional Bundle
        )

        setContent {
            MaterialTheme {
                ProvideWindowInsets {
                    TopScreen(topViewModel)
                }
            }
        }
    }

    public override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    public override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    public override fun onStop() {
        super.onStop()
        // (see "stay in sync with the MediaSession")
        MediaControllerCompat.getMediaController(this)
            ?.unregisterCallback(controllerCallback)

        mediaBrowser.disconnect()
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            // TODO Push metadata to a flow observed inside Jetpack Compose
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            // TODO Push playback state to a flow observed inside Jetpack Compose
        }

        override fun onSessionDestroyed() {
            mediaBrowser.disconnect()
            Timber.e("Session destroyed in Activity.")
            // maybe schedule a reconnection using a new MediaBrowser instance
        }
    }

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            // Get the token for the MediaSession
            mediaBrowser.sessionToken.also { token ->
                Timber.v("Connected to session with Token: $token")

                // Create a MediaControllerCompat
                val mediaController = MediaControllerCompat(
                    this@RemasterActivity, // Context
                    token
                )

                // Save the controller
                MediaControllerCompat.setMediaController(this@RemasterActivity, mediaController)
            }

            // Finish building the UI
            // TODO Use Jetpack Compose buttons to call methods on `mediaController.transportControls`.


            // Register a Callback to stay in sync
            val mediaController = MediaControllerCompat.getMediaController(this@RemasterActivity)
            mediaController.registerCallback(controllerCallback)
        }

        override fun onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
            TODO()
        }

        override fun onConnectionFailed() {
            // The Service has refused our connection
            TODO()
        }
    }

    private fun setupPermissions() {
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                topViewModel.storagePermissionGranted()
            } else {
                topViewModel.storagePermissionDenied()
            }
        }

        when {
            isPermissionGranted() -> topViewModel.storagePermissionGranted()
            shouldExplainPermission() -> topViewModel.showPermissionExplanation()
            else -> permissionLauncher.launch(getPermissionName())
        }
    }

    private fun isPermissionGranted() =
        ContextCompat.checkSelfPermission(this, getPermissionName()) == GRANTED

    private fun shouldExplainPermission() =
        ActivityCompat.shouldShowRequestPermissionRationale(this, getPermissionName())

    private fun getPermissionName() = if (BuildConfig.DEBUG) {
        PERMISSION_WRITE
    } else {
        PERMISSION_READ
    }

    companion object {
        // just to shorten some lines above
        const val PERMISSION_READ = Manifest.permission.READ_EXTERNAL_STORAGE
        const val PERMISSION_WRITE = Manifest.permission.WRITE_EXTERNAL_STORAGE
        const val GRANTED = PackageManager.PERMISSION_GRANTED
    }
}