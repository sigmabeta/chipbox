package net.sigmabeta.chipbox.di

import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.sage.coroutines.SageDispatchers
import net.sigmabeta.sage.list.DelayManager
import net.sigmabeta.sage.list.SageScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchedulerImpl @Inject constructor(
    override val dispatchers: SageDispatchers,
    override val coroutineScope: CoroutineScope,
) : SageScheduler {
    override val delayManager: DelayManager = object : DelayManager {
        override fun shouldDelay(): Boolean = false
    }
}
