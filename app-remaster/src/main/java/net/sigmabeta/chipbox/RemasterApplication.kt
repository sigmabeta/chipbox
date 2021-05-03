package net.sigmabeta.chipbox

import android.annotation.TargetApi
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.View
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class RemasterApplication : Application() {
    /**
     * Calls the superclass constructor, then initializes the singleton
     * Dagger Components.
     */
    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())
        Timber.d("Starting Application.")
        Timber.d("Build type: %s", BuildConfig.BUILD_TYPE)

        Timber.d("Android version: %s", Build.VERSION.RELEASE)
        Timber.d("Device manufacturer: %s", Build.MANUFACTURER)
        Timber.d("Device model: %s", Build.MODEL)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupNotifications()
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun setupNotifications() {
        val name = "Playback"
        val description = "Displays media controls."
        val importance = NotificationManager.IMPORTANCE_LOW

        val channel = NotificationChannel(CHANNEL_ID_PLAYBACK, name, importance)
        channel.description = description

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun shouldShowDetailedErrors() = BuildConfig.DEBUG

    companion object {
        val CHANNEL_ID_PLAYBACK = BuildConfig.APPLICATION_ID + ".playback"
    }
}

fun Any.className() = this.javaClass.simpleName