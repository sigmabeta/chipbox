package net.sigmabeta.chipbox.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import dagger.android.AndroidInjection
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class RemasterActivity : AppCompatActivity(), HasAndroidInjector{
    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    override fun androidInjector() = androidInjector

    override fun onCreate(savedInstanceState: Bundle?) {
//        setTheme(R.style.ChipboxImmersive)
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_remaster)

        val displayMetrics = resources.displayMetrics
        val widthPixels = displayMetrics.widthPixels
        val heightPixels = displayMetrics.heightPixels

//        Timber.v("Device screen DPI: ${displayMetrics.densityDpi}")
//        Timber.v("Device screen scaling factor: ${displayMetrics.density}")
//        Timber.v("Device screen size: ${widthPixels}x$heightPixels")
//        Timber.v("Device screen size (scaled): ${(widthPixels / displayMetrics.density).toInt()}" +
//                "x${(heightPixels / displayMetrics.density).toInt()}")
    }
}