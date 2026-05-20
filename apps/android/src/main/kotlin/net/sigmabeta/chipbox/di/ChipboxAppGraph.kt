package net.sigmabeta.chipbox.di

import android.app.Application
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.sage.android.logging.AndroidHatchet
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
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface ChipboxAppGraph : ViewModelGraph {
    val appInfo: AppInfo
    val hatchet: Hatchet

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppInfo(): AppInfo = AppInfo(
        isDebug = BuildConfig.DEBUG,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildTimeMs = BuildConfig.BUILD_TIME_MS,
        buildBranch = BuildConfig.BUILD_BRANCH,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideHatchet(): Hatchet = AndroidHatchet()

    // Bridge for Hilt's @ApplicationContext qualifier: sage modules that take an
    // @ApplicationContext Context (resources, analytics) keep using the Hilt-style qualifier,
    // and Metro's interop recognises the meta-annotated @Qualifier — but the binding itself
    // has to come from somewhere. Application enters the graph via the factory below.
    @Provides
    @SingleIn(AppScope::class)
    @ApplicationContext
    fun provideAppContext(application: Application): Context = application

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): ChipboxAppGraph
    }
}
