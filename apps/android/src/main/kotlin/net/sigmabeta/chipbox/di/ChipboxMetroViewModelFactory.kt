package net.sigmabeta.chipbox.di

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass
import net.sigmabeta.sage.di.AppScope

/**
 * Chipbox's [MetroViewModelFactory] — the `androidx.lifecycle.ViewModelProvider.Factory` that
 * Metro plugs in to back `metroViewModel<T>()` calls in composables. Receives the three
 * multibinding maps from the graph (populated by `@ContributesIntoMap(AppScope::class)` on
 * each `@ViewModelKey`-annotated VM); base class dispatches to the right map and instantiates.
 *
 * Contributed as a `MetroViewModelFactory` binding via [ContributesBinding] so the
 * `ViewModelGraph.metroViewModelFactory` accessor resolves it; `@SingleIn(AppScope::class)`
 * matches the graph's scope.
 */
// The manual-assisted multibinding-map type exceeds detekt's 120-col limit inline; alias it
// (transparent to Metro and the MetroViewModelFactory override) so the constructor stays readable.
private typealias ManualAssistedFactoryProviders =
    Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ChipboxMetroViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders: ManualAssistedFactoryProviders,
) : MetroViewModelFactory()
