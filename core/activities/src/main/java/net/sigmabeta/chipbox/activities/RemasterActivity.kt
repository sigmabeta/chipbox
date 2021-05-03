package net.sigmabeta.chipbox.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class RemasterActivity : ComponentActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ChipboxImmersive)
        super.onCreate(savedInstanceState)

        val displayMetrics = resources.displayMetrics
        val widthPixels = displayMetrics.widthPixels
        val heightPixels = displayMetrics.heightPixels

        Timber.v("Device screen DPI: ${displayMetrics.densityDpi}")
        Timber.v("Device screen scaling factor: ${displayMetrics.density}")
        Timber.v("Device screen size: ${widthPixels}x$heightPixels")
        Timber.v("Device screen size (scaled): ${(widthPixels / displayMetrics.density).toInt()}" +
                "x${(heightPixels / displayMetrics.density).toInt()}")


//        setContent {
//            Text("Hello Compose")
//        }
    }
}