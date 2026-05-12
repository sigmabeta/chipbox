package net.sigmabeta.chipbox.coroutines

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.sage.coroutines.SageDispatchers
import net.sigmabeta.sage.list.DelayManager
import net.sigmabeta.sage.list.SageScheduler

@Singleton
class ChipboxScheduler @Inject constructor(
    override val dispatchers: SageDispatchers,
    override val coroutineScope: CoroutineScope,
) : SageScheduler {
    override val delayManager: DelayManager = object : DelayManager {
        override fun shouldDelay(): Boolean = false
    }
}
