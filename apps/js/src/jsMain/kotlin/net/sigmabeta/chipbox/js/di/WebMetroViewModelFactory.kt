package net.sigmabeta.chipbox.js.di

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
 * JS-side [MetroViewModelFactory] — backs `metroViewModel<T>()` calls in the browser app. Same
 * shape as `JvmMetroViewModelFactory` / `ChipboxMetroViewModelFactory`; identical 7-line class,
 * each entry point keeps its own copy rather than sharing through a common module.
 */
private typealias ManualAssistedFactoryProviders =
    Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class WebMetroViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders: ManualAssistedFactoryProviders,
) : MetroViewModelFactory()
