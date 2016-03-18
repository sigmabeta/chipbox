package net.sigmabeta.chipbox.dagger

import android.app.Application
import net.sigmabeta.chipbox.dagger.component.AppComponent
import net.sigmabeta.chipbox.dagger.component.DaggerAppComponent
import net.sigmabeta.chipbox.dagger.module.AppModule
import net.sigmabeta.chipbox.util.logVerbose

object Initializer {
    fun initAppComponent(application: Application): AppComponent {
        logVerbose("[Initializer] Initializing Dagger AppComponent.")

        return DaggerAppComponent.builder()
                .appModule(AppModule(application))
                .build()
    }
}
