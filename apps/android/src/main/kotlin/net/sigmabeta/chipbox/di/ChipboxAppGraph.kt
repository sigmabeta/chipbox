package net.sigmabeta.chipbox.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The Metro equivalent of Hilt's `SingletonComponent` — early-stage stub during the
 * Hilt → Metro migration (see docs/metro-migration.md, Milestone 1).
 *
 * Today this graph has exactly one binding: a marker `metroSmokeTest: String`. Its only
 * purpose is to prove that Metro's compiler plugin is wired correctly in apps/android
 * alongside the existing Hilt processor. As bindings move out of `AndroidAppModule` and
 * the rest of the Hilt graph in Milestones 2+, they land here (or in `@ContributesTo`-
 * annotated module interfaces aggregated into this graph) and the Hilt versions retire.
 */
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface ChipboxAppGraph {
    val metroSmokeTest: String

    @Provides
    @SingleIn(AppScope::class)
    fun provideMetroSmokeTest(): String = "metro is wired"
}
