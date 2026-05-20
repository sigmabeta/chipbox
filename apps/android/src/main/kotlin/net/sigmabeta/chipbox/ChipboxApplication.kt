package net.sigmabeta.chipbox

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.zacsweers.metro.createGraphFactory
import net.sigmabeta.chipbox.di.ChipboxAppGraph

@HiltAndroidApp
class ChipboxApplication : Application() {
    /**
     * Application-wide Metro graph. Owned here so any Activity / Service can pull it via
     * `(application as ChipboxApplication).appGraph`, mirroring the entry-point pattern Hilt
     * uses (`SingletonComponent`-scoped bindings, instantiated once, lifetime = process).
     *
     * `lazy` so the graph is constructed on first use, not at process start — keeps cold-start
     * impact minimal during the transition while Hilt continues to do most of the work. The
     * factory call binds `this` Application into the graph; sage modules that take
     * `@ApplicationContext Context` get the Context from `provideAppContext` in the graph.
     */
    val appGraph: ChipboxAppGraph by lazy {
        createGraphFactory<ChipboxAppGraph.Factory>().create(this)
    }
}
