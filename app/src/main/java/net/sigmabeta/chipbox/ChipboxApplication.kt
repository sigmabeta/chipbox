package net.sigmabeta.chipbox

import android.annotation.TargetApi
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.View
import com.crashlytics.android.Crashlytics
import io.fabric.sdk.android.Fabric
import io.realm.Realm
import io.realm.RealmConfiguration
import net.sigmabeta.chipbox.dagger.Initializer
import net.sigmabeta.chipbox.dagger.component.AppComponent
import timber.log.Timber
import java.io.File


public class ChipboxApplication : Application() {
    lateinit var appComponent: AppComponent

    /**
     * Calls the superclass constructor, then initializes the singleton
     * Dagger Components.
     */
    override fun onCreate() {
        super.onCreate()

        if (findOldDbFile()) {
            clearOldDbFile()
            clearOldImages()
        }

        Timber.plant(Timber.DebugTree())
        Timber.d("Starting Application.")
        Timber.d("Build type: %s", BuildConfig.BUILD_TYPE)

        Timber.d("Android version: %s", Build.VERSION.RELEASE)
        Timber.d("Device manufacturer: %s", Build.MANUFACTURER)
        Timber.d("Device model: %s", Build.MODEL)

        // TODO load these on demand?
        System.loadLibrary("gme")
        System.loadLibrary("vgm")

        Realm.init(this)
        val realmConfig = RealmConfiguration.Builder().build()
        Realm.setDefaultConfiguration(realmConfig)

        val fabric = Fabric.Builder(this)
                .kits(Crashlytics())
                .debuggable(BuildConfig.DEBUG)
                .build()

        Fabric.with(fabric)

        appComponent = Initializer.initAppComponent(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupNotifications()
        }
    }

    private fun findOldDbFile(): Boolean {
        val directory = filesDir
        val oldDbFile = File(directory.absolutePath + "/chipbox.db")

        return oldDbFile.exists()
    }

    private fun clearOldDbFile() {
        val directory = filesDir
        val oldDbFile = File(directory.absolutePath + "/chipbox.db")

        oldDbFile.delete()
    }

    private fun clearOldImages() {
        val directory = filesDir
        val imagesFolder = File(directory.absolutePath + "/images")

        imagesFolder.deleteRecursively()
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

fun View.name() : String {
    return try {
        this.resources.getResourceEntryName(id)
    } catch (e: Resources.NotFoundException) {
        "Unknown ${className()}"
    }
}