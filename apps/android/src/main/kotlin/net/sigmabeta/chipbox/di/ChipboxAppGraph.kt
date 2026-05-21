package net.sigmabeta.chipbox.di

import android.app.Application
import android.content.Context
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import net.sigmabeta.chipbox.contentsource.AndroidFileContentSource
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.services.api.LibraryBrowser
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/**
 * Application-wide Metro dependency graph (see docs/metro-migration.md). Owns every
 * `AppScope`-scoped binding the app needs — both for `metroViewModel<T>()` resolution
 * (via the inherited [ViewModelGraph] multibindings) and for hand-rolled injection at
 * the four `Application` / `Activity` / `Service` / `ContentProvider` entry points: those
 * cast `application` to [net.sigmabeta.chipbox.ChipboxApplication] and read accessors here
 * directly. The pattern replaces Hilt's `@HiltAndroidApp` + `@AndroidEntryPoint` /
 * `@EntryPoint` machinery removed in M6.
 */
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface ChipboxAppGraph : ViewModelGraph {
    val appInfo: AppInfo
    val hatchet: Hatchet
    val stringProvider: StringProvider

    // ChipboxPlaybackService dependencies — pulled in `ChipboxPlaybackService.onCreate()`
    // (post-M6, no more @Inject lateinit).
    val libraryBrowser: LibraryBrowser
    val director: Director

    // ArtworkProvider dependencies — pulled lazily in `ArtworkProvider.openFile()` since
    // ContentProvider construction is process-init and can't synchronously resolve
    // an AppScope graph that's lazy-built.
    val repository: Repository
    val fileContentSource: AndroidFileContentSource

    // Bind Context from Application: contributed modules that take a Context (resources,
    // analytics, datastore) get the Application Context routed through this @Provides.
    // Metro's single-scope graph doesn't need a qualifier — the old Hilt `@ApplicationContext`
    // disambiguated between SingletonComponent vs ActivityComponent Contexts, which doesn't
    // exist post-M6.
    @Provides
    @SingleIn(AppScope::class)
    fun provideAppContext(application: Application): Context = application

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): ChipboxAppGraph
    }
}
