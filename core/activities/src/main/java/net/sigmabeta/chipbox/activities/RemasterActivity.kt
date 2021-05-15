package net.sigmabeta.chipbox.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material.MaterialTheme
import androidx.core.view.WindowCompat
import com.google.accompanist.insets.ProvideWindowInsets
import dagger.hilt.android.AndroidEntryPoint
import net.sigmabeta.chipbox.features.top.TopScreen
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RemasterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ChipboxImmersive)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        val displayMetrics = resources.displayMetrics
        val widthPixels = displayMetrics.widthPixels
        val heightPixels = displayMetrics.heightPixels

        Timber.v("Device screen DPI: ${displayMetrics.densityDpi}")
        Timber.v("Device screen scaling factor: ${displayMetrics.density}")
        Timber.v("Device screen size: ${widthPixels}x$heightPixels")

        setContent {
            MaterialTheme {
                ProvideWindowInsets {
                    TopScreen()
                }
            }
        }
    }
}