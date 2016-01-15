package net.sigmabeta.chipbox

import android.app.Application
import android.os.Build
import com.squareup.leakcanary.LeakCanary
import net.sigmabeta.chipbox.dagger.Initializer
import net.sigmabeta.chipbox.dagger.component.AppComponent
import net.sigmabeta.chipbox.util.logDebug
import net.sigmabeta.chipbox.util.logVerbose

public class ChipboxApplication : Application() {
    /**
     * Static methods and members go in a 'companion' object.
     */
    companion object {
        @JvmField var appComponent: AppComponent? = null
    }

    /**
     * Calls the superclass constructor, then initializes the singleton
     * Dagger Components.
     */
    override fun onCreate() {
        super.onCreate()

        System.loadLibrary("gme")

        LeakCanary.install(this);

        logDebug("[ChipboxApplication] Starting Application.")
        logDebug("[ChipboxApplication] Build type: ${BuildConfig.BUILD_TYPE}")

        logDebug("[ChipboxApplication] Android version: ${Build.VERSION.RELEASE}")
        logDebug("[ChipboxApplication] Device manufacturer: ${Build.MANUFACTURER}")
        logDebug("[ChipboxApplication] Device model: ${Build.MODEL}")

        appComponent = Initializer.initAppComponent(this)
        if (appComponent != null) {
            logVerbose("[ChipboxApplication] Initialized Dagger AppComponent.")
        }
    }
}
