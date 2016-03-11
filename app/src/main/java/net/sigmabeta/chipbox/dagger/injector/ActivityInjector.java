package net.sigmabeta.chipbox.dagger.injector;

import net.sigmabeta.chipbox.ChipboxApplication;
import net.sigmabeta.chipbox.util.LogKt;
import net.sigmabeta.chipbox.view.activity.FileListActivity;
import net.sigmabeta.chipbox.view.activity.GameActivity;
import net.sigmabeta.chipbox.view.activity.MainActivity;
import net.sigmabeta.chipbox.view.activity.NavigationActivity;
import net.sigmabeta.chipbox.view.activity.PlayerActivity;
import net.sigmabeta.chipbox.view.activity.ScanActivity;
import net.sigmabeta.chipbox.view.interfaces.FileListView;
import net.sigmabeta.chipbox.view.interfaces.GameView;
import net.sigmabeta.chipbox.view.interfaces.MainView;
import net.sigmabeta.chipbox.view.interfaces.NavigationView;
import net.sigmabeta.chipbox.view.interfaces.PlayerActivityView;
import net.sigmabeta.chipbox.view.interfaces.ScanView;

public class ActivityInjector {
    public static void inject(MainView view) {
        LogKt.logVerbose("[ActivityInjector] Injecting MainView.");

        ChipboxApplication.activityComponent
                .inject((MainActivity) view);
    }

    public static void inject(FileListView view) {
        LogKt.logVerbose("[ActivityInjector] Injecting FileListView.");

        ChipboxApplication.activityComponent
                .inject((FileListActivity) view);
    }

    public static void inject(NavigationView view) {
        LogKt.logVerbose("[ActivityInjector] Injecting NavigationView.");

        ChipboxApplication.activityComponent
                .inject((NavigationActivity) view);
    }

    public static void inject(PlayerActivityView view) {
        LogKt.logVerbose("[ActivityInjector] Injecting PlayerActivityView.");

        ChipboxApplication.activityComponent
                .inject((PlayerActivity) view);
    }

    public static void inject(GameView view) {
        LogKt.logVerbose("[ActivityInjector] Injecting GameView.");

        ChipboxApplication.activityComponent
                .inject((GameActivity) view);
    }

    public static void inject(ScanView view) {
        LogKt.logVerbose("[ActivityInjector] Injecting ScanView.");

        ChipboxApplication.activityComponent
                .inject((ScanActivity) view);
    }
}
