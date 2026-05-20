package net.sigmabeta.chipbox.jvm

import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.jvm.di.JvmChipboxComponent
import net.sigmabeta.chipbox.ui.vm.ChipboxViewModel
import net.sigmabeta.chipbox.ui.vm.ViewModelProvider
import kotlin.reflect.KClass

/**
 * JVM-side [ViewModelProvider] backed by the plain-Dagger [JvmChipboxComponent]. Dispatches
 * a `ChipboxViewModel` type to the corresponding component accessor. Today there's just one
 * VM (the desktop bootstrap's [HelloViewModel]); once feature ports add more, the
 * `when` here grows in lockstep with the component's accessors — or migrates to a Dagger
 * Multibindings `Map<KClass<*>, Provider<ChipboxViewModel>>` keyed by class so neither side
 * has to be touched per new VM.
 */
class JvmViewModelProvider(
    private val component: JvmChipboxComponent,
) : ViewModelProvider {
    @Composable
    @Suppress("UNCHECKED_CAST")
    override fun <T : ChipboxViewModel> get(type: KClass<T>): T = when (type) {
        HelloViewModel::class -> component.helloViewModel() as T
        else -> error("No ChipboxViewModel registered for $type in JvmViewModelProvider.")
    }
}
