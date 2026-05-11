package net.sigmabeta.chipbox.activities

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.MaterialTheme
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import net.sigmabeta.chipbox.services.ChipboxPlaybackService
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Inject
import net.sigmabeta.chipbox.styles.R as StylesR

@AndroidEntryPoint
class RemasterActivity : ComponentActivity() {
    @Inject
    lateinit var topViewModel: TopViewModel

    @Inject
    lateinit var hatchet: Hatchet

    lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var directoryLauncher: ActivityResultLauncher<Uri?>

    private var browserFuture: ListenableFuture<MediaBrowser>? = null
    private var browser: MediaBrowser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(StylesR.style.ChipboxImmersive)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        val displayMetrics = resources.displayMetrics
        val widthPixels = displayMetrics.widthPixels
        val heightPixels = displayMetrics.heightPixels

        directoryLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            if (uri != null) {
                topViewModel.directoryPermissionGranted(uri)
            }
        }

        hatchet.v("Device screen DPI: ${displayMetrics.densityDpi}")
        hatchet.v("Device screen scaling factor: ${displayMetrics.density}")
        hatchet.v("Device screen size: ${widthPixels}x$heightPixels")

        setContent {
            MaterialTheme {
                ProvideWindowInsets {
                    TopScreen(topViewModel) { directoryLauncher.launch(null) }
                }
            }
        }
    }

    public override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, ChipboxPlaybackService::class.java))
        val future = MediaBrowser.Builder(this, token).buildAsync()
        browserFuture = future
        future.addListener(
            {
                browser = future.get()
                browser?.addListener(playerListener)
            },
            MoreExecutors.directExecutor(),
        )
    }

    public override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    public override fun onStop() {
        super.onStop()
        browser?.removeListener(playerListener)
        browserFuture?.let { MediaBrowser.releaseFuture(it) }
        browserFuture = null
        browser = null
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // TODO Push metadata to a flow observed inside Jetpack Compose
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // TODO Push playback state to a flow observed inside Jetpack Compose
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // TODO Push play/pause to a flow observed inside Jetpack Compose
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
        const val PERMISSION_READ = Manifest.permission.READ_EXTERNAL_STORAGE
        const val PERMISSION_WRITE = Manifest.permission.WRITE_EXTERNAL_STORAGE
        const val GRANTED = PackageManager.PERMISSION_GRANTED
    }
}
