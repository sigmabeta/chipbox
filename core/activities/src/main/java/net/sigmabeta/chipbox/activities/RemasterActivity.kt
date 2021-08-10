package net.sigmabeta.chipbox.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RemasterActivity : ComponentActivity() {
    @Inject
    lateinit var topViewModel: TopViewModel

    lateinit var permissionLauncher: ActivityResultLauncher<String>

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

        setContent {
            MaterialTheme {
                ProvideWindowInsets {
                    TopScreen(topViewModel)
                }
            }
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
            else -> permissionLauncher.launch(PERMISSION)
        }
    }

    private fun isPermissionGranted() =
        ContextCompat.checkSelfPermission(this, PERMISSION) == GRANTED

    private fun shouldExplainPermission() =
        ActivityCompat.shouldShowRequestPermissionRationale(this, PERMISSION)


    companion object {
        // just to shorten some lines above
        const val PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE
        const val GRANTED = PackageManager.PERMISSION_GRANTED
    }
}