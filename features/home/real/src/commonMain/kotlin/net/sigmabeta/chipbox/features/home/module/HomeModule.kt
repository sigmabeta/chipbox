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
 *
 * **Keep [state] cheap — fast loading is a hard requirement.** Home collects every module's flow
 * on open, so a row must not pull the whole catalog and filter/shuffle in memory (e.g.
 * `repository.getAllGames()` then `.filter`/`.shuffled`). Push the filter, limit, and any random
 * pick down into a bounded, indexed repository query; if one doesn't exist yet, add it to
 * `Repository` rather than post-filtering a list-everything call.
 * `RecentlyAddedGamesHomeModule` over `Repository.getRecentlyAddedGames` is the reference example.
 */
interface HomeModule {
    /** Stable identity used to key the section's state slot. */
    val id: String

    /** Lower values render earlier on screen. */
    val priority: Int

    /**
     * Whether to prepend a [net.sigmabeta.sage.components.SectionHeaderListModel] above the
     * module's items. Defaults to true; override to false for modules whose content already
     * carries its own heading (e.g. a now-playing card with the track title on it).
     */
    val showHeader: Boolean
        get() = true

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
