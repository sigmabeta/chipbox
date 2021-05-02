package net.sigmabeta.chipbox.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.android.AndroidInjection
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import net.sigmabeta.chipbox.repository.Repository
import timber.log.Timber
import javax.inject.Inject

class RemasterActivity : ComponentActivity(), HasAndroidInjector{
    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    override fun androidInjector() = androidInjector

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ChipboxImmersive)
        AndroidInjection.inject(this)
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