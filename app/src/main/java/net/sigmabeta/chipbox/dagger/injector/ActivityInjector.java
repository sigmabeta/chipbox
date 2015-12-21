package net.sigmabeta.chipbox.dagger.injector;

import net.sigmabeta.chipbox.ChipboxApplication;
import net.sigmabeta.chipbox.presenter.module.FileListModule;
import net.sigmabeta.chipbox.presenter.module.GameModule;
import net.sigmabeta.chipbox.presenter.module.MainModule;
import net.sigmabeta.chipbox.presenter.module.NavigationModule;
import net.sigmabeta.chipbox.presenter.module.PlayerActivityModule;
import net.sigmabeta.chipbox.util.LogKt;
import net.sigmabeta.chipbox.view.activity.FileListActivity;
import net.sigmabeta.chipbox.view.activity.GameActivity;
import net.sigmabeta.chipbox.view.activity.MainActivity;
import net.sigmabeta.chipbox.view.activity.NavigationActivity;
import net.sigmabeta.chipbox.view.activity.PlayerActivity;
import net.sigmabeta.chipbox.view.interfaces.FileListView;
import net.sigmabeta.chipbox.view.interfaces.GameView;
import net.sigmabeta.chipbox.view.interfaces.MainView;
import net.sigmabeta.chipbox.view.interfaces.NavigationView;
import net.sigmabeta.chipbox.view.interfaces.PlayerActivityView;

public class ActivityInjector {
    public static void inject(MainView view) {
        LogKt.logVerbose("[ActivityInjector] Injecting MainView.");

        ChipboxApplication.appComponent
                .plus(new MainModule(view))
                .inject((MainActivity) view);
    }

    public static void inject(FileListView view) {
        LogKt.logVerbose("[ActivityInjector] Injecting FileListView.");

        ChipboxApplication.appComponent
                .plus(new FileListModule(view))
                .inject((FileListActivity) view);
    }

    public static void inject(NavigationView view) {
        LogKt.logVerbose("[ActivityInjector] Injecting NavigationView.");

        ChipboxApplication.appComponent
                .plus(new NavigationModule(view))
                .inject((NavigationActivity) view);
    }

    public static void inject(PlayerActivityView view) {
        LogKt.logVerbose("[ActivityInjector] Injecting PlayerActivityView.");

        ChipboxApplication.appComponent
                .plus(new PlayerActivityModule(view))
                .inject((PlayerActivity) view);
    }

    public static void inject(GameView view) {
        LogKt.logVerbose("[ActivityInjector] Injecting GameView.");

        ChipboxApplication.appComponent
                .plus(new GameModule(view))
                .inject((GameActivity) view);
    }
}
