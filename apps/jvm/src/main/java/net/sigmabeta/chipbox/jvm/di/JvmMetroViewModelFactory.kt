package net.sigmabeta.chipbox.jvm.di

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
 * JVM-side [MetroViewModelFactory] — the `androidx.lifecycle.ViewModelProvider.Factory` that
 * backs `metroViewModel<T>()` calls in Compose Multiplatform desktop screens. JVM analog of
 * `ChipboxMetroViewModelFactory` on Android; identical shape, both sides keep their own copy
 * rather than sharing through a common module (it's a 7-line class, not worth a third module).
 */
// The manual-assisted multibinding-map type exceeds detekt's 120-col limit inline; alias it
// (transparent to Metro and the MetroViewModelFactory override) so the constructor stays readable.
private typealias ManualAssistedFactoryProviders =
    Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class JvmMetroViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders: ManualAssistedFactoryProviders,
) : MetroViewModelFactory()
