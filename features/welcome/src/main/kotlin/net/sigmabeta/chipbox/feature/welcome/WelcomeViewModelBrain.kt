package net.sigmabeta.chipbox.feature.welcome

import net.sigmabeta.sage.analytics.Analytics
import net.sigmabeta.sage.analytics.AnalyticsScreen
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.list.ListViewModelBrain
import net.sigmabeta.sage.list.SageScheduler
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

class WelcomeViewModelBrain(
    stringProvider: StringProvider,
    analytics: Analytics,
    hatchet: Hatchet,
    scheduler: SageScheduler,
) : ListViewModelBrain(
    stringProvider,
    analytics,
    hatchet,
    scheduler,
) {
    override val screenIdentifier = AnalyticsScreen.ABOUT

    override fun initialState(): ListState = WelcomeState()

    override fun handleAction(action: SageAction) = Unit
}
