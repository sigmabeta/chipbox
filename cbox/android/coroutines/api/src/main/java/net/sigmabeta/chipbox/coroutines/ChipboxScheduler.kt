package net.sigmabeta.chipbox.coroutines

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.sage.coroutines.SageDispatchers
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.list.DelayManager
import net.sigmabeta.sage.list.SageScheduler

@SingleIn(AppScope::class)
class ChipboxScheduler @Inject constructor(
    override val coroutineScope: CoroutineScope,
    override val dispatchers: SageDispatchers,
    override val delayManager: DelayManager = object : DelayManager {
        override fun shouldDelay(): Boolean = false
    }
) : SageScheduler
