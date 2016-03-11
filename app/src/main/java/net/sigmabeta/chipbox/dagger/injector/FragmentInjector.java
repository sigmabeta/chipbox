package net.sigmabeta.chipbox.dagger.injector;


import net.sigmabeta.chipbox.ChipboxApplication;
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

        ChipboxApplication.fragmentComponent
                .inject((GameGridFragment) view);
    }

    public static void inject(ArtistListView view) {
        LogKt.logVerbose("[FragmentInjector] Injecting ArtistListView.");

        ChipboxApplication.fragmentComponent
                .inject((ArtistListFragment) view);
    }

    public static void inject(SongListView view) {
        LogKt.logVerbose("[FragmentInjector] Injecting SongListView.");

        ChipboxApplication.fragmentComponent
                .inject((SongListFragment) view);
    }

    public static void inject(PlatformListView view) {
        LogKt.logVerbose("[FragmentInjector] Injecting PlatformListView.");

        ChipboxApplication.fragmentComponent
                .inject((PlatformListFragment) view);
    }

    public static void inject(PlayerFragmentView view) {
        LogKt.logVerbose("[FragmentInjector] Injecting PlayerFragmentView.");

        ChipboxApplication.fragmentComponent
                .inject((PlayerFragment) view);
    }
}
