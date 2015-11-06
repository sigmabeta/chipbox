package net.sigmabeta.chipbox.dagger;

import android.app.Application;

import net.sigmabeta.chipbox.dagger.component.AppComponent;
import net.sigmabeta.chipbox.dagger.component.DaggerAppComponent;
import net.sigmabeta.chipbox.dagger.module.AppModule;
import net.sigmabeta.chipbox.util.LogKt;

public class Initializer {
    public static AppComponent initAppComponent(Application application) {
        LogKt.logVerbose("[Initializer] Initializing Dagger AppComponent.");

        return DaggerAppComponent.builder()
                .appModule(new AppModule(application))
                .build();
    }
}
