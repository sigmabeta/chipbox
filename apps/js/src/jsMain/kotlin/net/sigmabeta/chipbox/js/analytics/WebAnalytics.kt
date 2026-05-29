package net.sigmabeta.chipbox.js.analytics

import net.sigmabeta.sage.analytics.Analytics
import net.sigmabeta.sage.analytics.AnalyticsScreenId
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.appcomm.SageEvent
import net.sigmabeta.sage.logging.Hatchet

/**
 * No-op [Analytics] for the browser — every call routes to [Hatchet] so analytics events show
 * up in the console. Same shape as `sage/fake/analytics`'s `NoopAnalytics`, inlined here
 * because that module is android-only today; once it goes KMP this class can be dropped.
 */
class WebAnalytics(private val hatchet: Hatchet) : Analytics {
    override fun logScreenView(action: SageAction, screen: AnalyticsScreenId) {
        hatchet.v("Screen view: $screen via $action")
    }

    override fun logAction(action: SageAction, fromScreen: AnalyticsScreenId) {
        hatchet.d("Action: $action from $fromScreen")
    }

    override fun logEvent(event: SageEvent) {
        hatchet.d("Event: $event")
    }

    override fun logAutoRefresh() {
        hatchet.d("Refresh performed automatically.")
    }

    override fun logError(failedOperationName: String, errorString: String, error: Throwable) {
        hatchet.e("Analytics error log: $failedOperationName | $errorString | $error")
    }
}
