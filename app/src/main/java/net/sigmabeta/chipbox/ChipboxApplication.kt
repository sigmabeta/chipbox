package net.sigmabeta.chipbox

import android.app.Application
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
    }
}
