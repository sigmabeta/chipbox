package net.sigmabeta.chipbox.dagger.injector;


import net.sigmabeta.chipbox.ChipboxApplication;
import net.sigmabeta.chipbox.backend.PlayerService;
import net.sigmabeta.chipbox.util.LogKt;
import net.sigmabeta.chipbox.view.interfaces.BackendView;

public class ServiceInjector {
    public static void inject(BackendView view) {
        LogKt.logVerbose("[ServiceInjector] Injecting BackendView.");

        ChipboxApplication.appComponent
                .inject((PlayerService) view);
    }
}
