package net.sigmabeta.chipbox.di

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.sage.coroutines.VglsDispatchers
import net.sigmabeta.sage.list.DelayManager
import net.sigmabeta.sage.list.VglsScheduler

@Singleton
class SchedulerImpl @Inject constructor(
    override val dispatchers: VglsDispatchers,
    override val coroutineScope: CoroutineScope,
) : VglsScheduler {
    override val delayManager: DelayManager = object : DelayManager {
        override fun shouldDelay(): Boolean = false
    }
}
