package net.sigmabeta.chipbox

import android.app.Application
import android.os.Build
import net.sigmabeta.chipbox.dagger.Initializer
import net.sigmabeta.chipbox.dagger.component.ActivityComponent
import net.sigmabeta.chipbox.dagger.component.AppComponent
import net.sigmabeta.chipbox.dagger.component.FragmentComponent
import net.sigmabeta.chipbox.util.logDebug
import net.sigmabeta.chipbox.util.logVerbose

public class ChipboxApplication : Application() {
    /**
     * Static methods and members go in a 'companion' object.
     */
    companion object {
        @JvmField var appComponent: AppComponent? = null
        @JvmField var activityComponent: ActivityComponent? = null
        @JvmField var fragmentComponent: FragmentComponent? = null
    }

    /**
     * Calls the superclass constructor, then initializes the singleton
     * Dagger Components.
     */
    override fun onCreate() {
        super.onCreate()

        System.loadLibrary("gme")

        logDebug("[ChipboxApplication] Starting Application.")
        logDebug("[ChipboxApplication] Build type: ${BuildConfig.BUILD_TYPE}")

        logDebug("[ChipboxApplication] Android version: ${Build.VERSION.RELEASE}")
        logDebug("[ChipboxApplication] Device manufacturer: ${Build.MANUFACTURER}")
        logDebug("[ChipboxApplication] Device model: ${Build.MODEL}")

        appComponent = Initializer.initAppComponent(this)
        if (appComponent != null) {
            logVerbose("[ChipboxApplication] Initialized Dagger AppComponent.")

            activityComponent = Initializer.initActivityComponent()
            fragmentComponent = Initializer.initFragmentComponent()
        }
    }
}
