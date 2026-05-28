package net.sigmabeta.chipbox.js.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/**
 * Metro graph for the JS/browser target — the JS twin of
 * [net.sigmabeta.chipbox.jvm.di.JvmChipboxGraph]. Caller-supplied [Hatchet] + [StringProvider]
 * enter via the factory's `@Provides` params: the logger has no platform deps and the strings
 * preload is suspend (Compose Multiplatform `getString`), so both are built in `JsMain` before
 * the graph exists. Every other binding — repository, content source, player chain,
 * settings/debug, scanner — comes from [WebModules], which pins fakes for everything that would
 * otherwise need native code or platform persistence.
 *
 * `@ContributesIntoMap` / `@ContributesIntoSet` discovery across the 89-module JS klib graph
 * works because chipbox bumped Kotlin to 2.3.21 and lifted the KT-82395 hint-codegen workaround
 * in `ChipboxFeatureRealPlugin` — JS klibs now ship the same hints JVM/Android do.
 */
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface WebChipboxGraph : ViewModelGraph {
    val hatchet: Hatchet
    val stringProvider: StringProvider

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides hatchet: Hatchet,
            @Provides stringProvider: StringProvider,
        ): WebChipboxGraph
    }
}
