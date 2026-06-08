package net.sigmabeta.chipbox.jvm.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import java.io.File
import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.crash.CrashReporter
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.persistence.PlaybackSessionPersister
import net.sigmabeta.chipbox.player.speaker.real.SourceDataLineSpeaker
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/**
 * Metro graph for the JVM/desktop target — the JVM equivalent of
 * [net.sigmabeta.chipbox.di.ChipboxAppGraph] on the Android side. Owns every `AppScope`-scoped
 * binding the desktop UI needs; consumed via `metroViewModel<T>()` (through the inherited
 * [ViewModelGraph]) and via direct accessors on `Main.kt` / `DesktopMain.kt`.
 *
 * Caller-supplied paths (db file, render-cache workdir) enter the graph through a
 * `@DependencyGraph.Factory` taking the two `@Named`-qualified params via `@Provides` factory
 * params.
 */
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface JvmChipboxGraph : ViewModelGraph {
    val hatchet: Hatchet
    val stringProvider: StringProvider
    val repository: Repository

    /** Build metadata; `isDebug` drives the debug window title/icon in `DesktopMain.kt`. */
    val appInfo: AppInfo

    /** Installs the uncaught-exception handler that serializes fatal crashes (see `Main.kt`). */
    val crashReporter: CrashReporter
    val librarySource: LocalFileContentSource
    val scanner: RealScanner
    val generator: RealGenerator

    /** Playback coordinator — observed by the OS media-control bridge (see `mediasession`). */
    val director: Director

    /** Saves the last session and restores it on the next launch (see `Main.kt`). */
    val playbackSessionPersister: PlaybackSessionPersister

    /** App-lifetime scope (Default dispatcher, SupervisorJob) from `JvmCoroutinesModule`; the
     *  media-control bridge collects the Director's flows on it. */
    val appScope: CoroutineScope

    /** Live speaker used by the desktop UI; bound to [Speaker] below. */
    val liveSpeaker: SourceDataLineSpeaker

    @Binds
    val RealGenerator.generatorBinding: Generator

    /** Director receives this binding via `DirectorModule.provideDirector(speaker = …)`. */
    @Binds
    val SourceDataLineSpeaker.speakerBinding: Speaker

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides @Named("dbPath") dbPath: String,
            @Provides @Named("workDir") workDir: File,
        ): JvmChipboxGraph
    }
}
