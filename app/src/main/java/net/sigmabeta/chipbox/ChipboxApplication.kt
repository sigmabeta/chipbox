package net.sigmabeta.chipbox

import android.annotation.TargetApi
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.View
import dagger.android.DaggerApplication
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import io.realm.Realm
import io.realm.RealmConfiguration
import net.sigmabeta.chipbox.dagger.Initializer
import net.sigmabeta.chipbox.dagger.component.AppComponent
import net.sigmabeta.chipbox.di.DaggerRemasterAppComponent
import net.sigmabeta.chipbox.di.RemasterAppModule
import timber.log.Timber
import javax.inject.Inject


public class ChipboxApplication : DaggerApplication(),  HasAndroidInjector {
    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

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

        Realm.init(this)
        val realmConfig = RealmConfiguration.Builder()
                .compactOnLaunch()
                .build()

        Realm.setDefaultConfiguration(realmConfig)

        appComponent = Initializer.initAppComponent(this)

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

    override fun applicationInjector() = DaggerRemasterAppComponent
            .factory()
            .create(RemasterAppModule(this))

    override fun androidInjector() = dispatchingAndroidInjector

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