package net.sigmabeta.chipbox.dagger.injector;


import net.sigmabeta.chipbox.ChipboxApplication;
import net.sigmabeta.chipbox.presenter.module.ArtistListModule;
import net.sigmabeta.chipbox.presenter.module.GameListModule;
import net.sigmabeta.chipbox.presenter.module.PlatformListModule;
import net.sigmabeta.chipbox.presenter.module.PlayerFragmentModule;
import net.sigmabeta.chipbox.presenter.module.SongListModule;
import net.sigmabeta.chipbox.util.LogKt;
import net.sigmabeta.chipbox.view.fragment.ArtistListFragment;
import net.sigmabeta.chipbox.view.fragment.GameGridFragment;
import net.sigmabeta.chipbox.view.fragment.PlatformListFragment;
import net.sigmabeta.chipbox.view.fragment.PlayerFragment;
import net.sigmabeta.chipbox.view.fragment.SongListFragment;
import net.sigmabeta.chipbox.view.interfaces.ArtistListView;
import net.sigmabeta.chipbox.view.interfaces.GameListView;
import net.sigmabeta.chipbox.view.interfaces.PlatformListView;
import net.sigmabeta.chipbox.view.interfaces.PlayerFragmentView;
import net.sigmabeta.chipbox.view.interfaces.SongListView;

public class FragmentInjector {
    public static void inject(GameListView view) {
        LogKt.logVerbose("[ActivityInjector] Injecting GameListView.");

        ChipboxApplication.appComponent
                .plus(new GameListModule(view))
                .inject((GameGridFragment) view);
    }

    public static void inject(ArtistListView view) {
        LogKt.logVerbose("[FragmentInjector] Injecting ArtistListView.");

        ChipboxApplication.appComponent
                .plus(new ArtistListModule(view))
                .inject((ArtistListFragment) view);
    }

    public static void inject(SongListView view) {
        LogKt.logVerbose("[FragmentInjector] Injecting SongListView.");

        ChipboxApplication.appComponent
                .plus(new SongListModule(view))
                .inject((SongListFragment) view);
    }

    public static void inject(PlatformListView view) {
        LogKt.logVerbose("[FragmentInjector] Injecting PlatformListView.");

        ChipboxApplication.appComponent
                .plus(new PlatformListModule(view))
                .inject((PlatformListFragment) view);
    }

    public static void inject(PlayerFragmentView view) {
        LogKt.logVerbose("[FragmentInjector] Injecting PlayerFragmentView.");

        ChipboxApplication.appComponent
                .plus(new PlayerFragmentModule(view))
                .inject((PlayerFragment) view);
    }
}
