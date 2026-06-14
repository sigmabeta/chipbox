package net.sigmabeta.chipbox.uitest.harness

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass
import net.sigmabeta.sage.di.AppScope

private typealias ManualAssistedFactoryProviders =
    Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>

/**
 * Test-graph [MetroViewModelFactory] — the `androidx.lifecycle.ViewModelProvider.Factory` that
 * backs `metroViewModel<T>()` / `assistedMetroViewModel<T, F>()` in hosted screens. Identical
 * shape to the app's `JvmMetroViewModelFactory` / `ChipboxMetroViewModelFactory`; it just lives in
 * the test source set so [TestAppGraph] binds it instead of an app's copy. The three multibinding
 * maps are populated by every `@ContributesIntoMap` ViewModel on the test classpath.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class TestMetroViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders: ManualAssistedFactoryProviders,
) : MetroViewModelFactory()
