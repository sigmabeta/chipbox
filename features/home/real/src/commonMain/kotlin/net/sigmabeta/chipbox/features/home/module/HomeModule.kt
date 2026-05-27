package net.sigmabeta.chipbox.features.home.module

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.ListModel

/**
 * One pluggable row of the Home screen. The HomeViewModel collects each module's [state] flow
 * in parallel and patches the corresponding slot in [HomeState]; modules render independently
 * as their data arrives. Bind concrete implementations into `Set<HomeModule>` via Metro
 * `@Binds @IntoSet` so adding a row is a single-file change.
 */
interface HomeModule {
    /** Stable identity used to key the section's state slot. */
    val id: String

    /** Lower values render earlier on screen. */
    val priority: Int

    fun state(): Flow<LCE<HomeModuleSection>>
}

/**
 * A module's resolved row: the visible title plus the pre-built items that go inside the
 * horizontal scroller. Modules build the [ListModel]s (icon, aspect ratio, click action) — the
 * HomeState wraps them in a [net.sigmabeta.sage.components.HorizontalScrollerListModel] and
 * prepends a [net.sigmabeta.sage.components.SectionHeaderListModel].
 */
data class HomeModuleSection(
    val title: String,
    val items: ImmutableList<ListModel>,
)
