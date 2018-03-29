package net.sigmabeta.chipbox

import android.annotation.TargetApi
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.crashlytics.android.Crashlytics
import io.fabric.sdk.android.Fabric
import io.realm.Realm
import io.realm.RealmConfiguration
import net.sigmabeta.chipbox.dagger.Initializer
import net.sigmabeta.chipbox.dagger.component.AppComponent
import timber.log.Timber


public class ChipboxApplication : Application() {
    lateinit var appComponent: AppComponent

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

    @TargetApi(Build.VERSION_CODES.O)
    private fun setupNotifications() {
        val name = "Playback"
        val description = "Displays media controls."
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(CHANNEL_ID_PLAYBACK, name, importance)
        mChannel.description = description
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager = getSystemService(
                Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }

    fun shouldShowDetailedErrors() = BuildConfig.DEBUG

    companion object {
        val CHANNEL_ID_PLAYBACK = BuildConfig.APPLICATION_ID + ".playback"
    }
}

fun Any.className() = this.javaClass.simpleName