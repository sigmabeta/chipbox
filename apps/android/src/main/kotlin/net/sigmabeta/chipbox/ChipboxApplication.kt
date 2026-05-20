package net.sigmabeta.chipbox

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.zacsweers.metro.createGraph
import net.sigmabeta.chipbox.di.ChipboxAppGraph

@HiltAndroidApp
class ChipboxApplication : Application() {
    /**
     * Application-wide Metro graph. Owned here so any Activity / Service can pull it via
     * `(application as ChipboxApplication).appGraph`, mirroring the entry-point pattern Hilt
     * uses (`SingletonComponent`-scoped bindings, instantiated once, lifetime = process).
     *
     * `lazy` so the graph is constructed on first use, not at process start — keeps cold-start
     * impact minimal during the transition while Hilt continues to do most of the work.
     */
    val appGraph: ChipboxAppGraph by lazy { createGraph<ChipboxAppGraph>() }
}
