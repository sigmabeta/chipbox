package net.sigmabeta.chipbox.features.home.modules

import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.notif.NotifCategory
import net.sigmabeta.chipbox.notif.NotifManager
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.NotifListModel
import net.sigmabeta.sage.ui.StringProvider

/**
 * Top-of-Home row for in-app notifications and error messages. It renders whatever the injected
 * [NotifManager] currently holds (the clear button routes a `NotifClearClicked` action back to
 * [net.sigmabeta.chipbox.features.home.HomeViewModel]) and hides itself completely when there are
 * none — unlike the content modules, there is no loading state to show.
 *
 * Notifications are full-width cards in the list model, but multiple notifications still go into
 * the usual horizontal scroller (the Home module contract); a single one gets the full screen
 * width through [net.sigmabeta.chipbox.features.home.HomeState]'s single-item special case.
 */
class NotifHomeModule @Inject constructor(
    private val notifManager: NotifManager,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    // Notifications carry their own title/card styling; a section header would just be noise.
    override val showHeader = false

    override fun state(): Flow<LCE<HomeModuleSection>> = notifManager.notifState
        .map { notifState ->
            val notifs = notifState.notifs.values.toList()
            if (notifs.isEmpty()) {
                LCE.Uninitialized
            } else {
                LCE.Content(
                    HomeModuleSection(
                        title = stringProvider.getString(ChipboxStringId.HOME_SECTION_NOTIFICATIONS),
                        items = notifs.map { notif ->
                            NotifListModel(
                                dataId = notif.id,
                                title = stringProvider.getString(notif.title),
                                description = notif.description,
                                actionLabel = "",
                                action = null,
                                isError = notif.category == NotifCategory.ERROR,
                            )
                        }.toImmutableList(),
                    ),
                )
            }
        }
        .distinctUntilChanged()

    private companion object {
        const val ID = "notifications"

        // Above Now Playing (0): a notification about something that went wrong should be the first
        // thing on the screen.
        const val PRIORITY = -100
    }
}
