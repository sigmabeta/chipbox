package net.sigmabeta.chipbox.di

import android.app.Application
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import javax.inject.Singleton
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

/**
 * The Metro equivalent of Hilt's `SingletonComponent` (see docs/metro-migration.md).
 *
 * Currently exposes a small subset of the app's singleton bindings — the ones with no
 * cross-module dependencies, so they can land here without the rest of the Hilt graph also
 * needing to be Metro-aware. Hilt continues to own the rest until the bulk module sweep
 * in Milestone 4; bindings that exist on both sides (AppInfo, Hatchet today) are duplicated
 * during the transition — same `BuildConfig` values, same `AndroidHatchet`, so Metro
 * consumers and Hilt consumers see equivalent instances.
 *
 * Extends [ViewModelGraph] from `metrox-viewmodel`, which adds three multibinding maps
 * (`viewModelProviders`, `assistedFactoryProviders`, `manualAssistedFactoryProviders`) plus a
 * `metroViewModelFactory: MetroViewModelFactory` accessor. The maps fill in from
 * `@ContributesIntoMap(AppScope::class)` annotations on individual VMs (see
 * `SettingsViewModel`); the factory comes from [ChipboxMetroViewModelFactory] via
 * `@ContributesBinding(AppScope::class)`.
 */
// Graph carries both @SingleIn(AppScope::class) (Metro-native) and @Singleton (Hilt-style)
// scope markers. During the migration, @ContributesTo modules use the existing @Singleton
// annotations untouched — Metro treats `javax.inject.Singleton` as its own scope, distinct
// from AppScope, and refuses to wire bindings from a non-matching scope into the graph.
// Adding @Singleton to the graph itself lets it accept both kinds of scoped binding. Once
// Milestone 6 drops Hilt entirely and the codebase rewrites @Singleton → @SingleIn(AppScope),
// the @Singleton annotation here goes away.
@Singleton
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface ChipboxAppGraph : ViewModelGraph {
    val appInfo: AppInfo
    val hatchet: Hatchet

    // Bridge for Hilt's @ApplicationContext qualifier: sage modules that take an
    // @ApplicationContext Context (resources, analytics) keep using the Hilt-style qualifier,
    // and Metro's interop recognises the meta-annotated @Qualifier — but the binding itself
    // has to come from somewhere. Application enters the graph via the factory below.
    @Provides
    @Singleton
    @ApplicationContext
    fun provideAppContext(application: Application): Context = application

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): ChipboxAppGraph
    }
}
