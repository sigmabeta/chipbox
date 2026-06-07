package net.sigmabeta.chipbox.features.crashlog.real

import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.crash.CrashReport
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.components.CollapsibleDetailsListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class CrashLogState(
    val crashes: List<CrashReport> = emptyList(),
) : ListState() {
    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.CRASH_LOG_SCREEN_TITLE),
        shouldShowBack = true,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> {
        if (crashes.isEmpty()) {
            return listOf(
                EmptyStateListModel(
                    icon = Icon.Warning,
                    explanation = stringProvider.getString(ChipboxStringId.CRASH_LOG_EMPTY),
                    showCrossOut = false,
                )
            )
        }
        return crashes.map(::crashRow)
    }

    // Each crash is one collapsible row: the title is a one-line summary (it ellipsizes), the full
    // stack trace and context live in the expandable detail items. The timestamp is unique per
    // report (it's the filename key + the prune sort key), so it's a stable dataId.
    private fun crashRow(report: CrashReport) = CollapsibleDetailsListModel(
        dataId = report.timestampMs,
        title = rowTitle(report),
        detailItems = detailItems(report).toImmutableList(),
        initiallyCollapsed = true,
    )

    // "IllegalStateException · 2026-06-07 14:23:01"
    private fun rowTitle(report: CrashReport): String =
        listOf(simpleClassName(report.exceptionClass), formatCrashTimestamp(report.timestampMs))
            .joinToString(SEPARATOR)

    private fun detailItems(report: CrashReport): List<String> = buildList {
        report.message?.takeIf { it.isNotBlank() }?.let(::add)
        add(buildMetadata(report))
        add(report.stackTrace.trimEnd())
        if (report.recentErrors.isNotEmpty()) add(buildRecentErrors(report))
    }

    // "v1.4.2 (beta) · debug · thread main"
    private fun buildMetadata(report: CrashReport): String {
        val build = "v${report.appVersionName} (${report.appBuildBranch})"
        val flavor = if (report.isDebugBuild) "debug" else "release"
        return listOf(build, flavor, "thread ${report.threadName}").joinToString(SEPARATOR)
    }

    private fun buildRecentErrors(report: CrashReport): String =
        report.recentErrors.joinToString(separator = "\n", prefix = "Recent log:\n") { error ->
            listOf(error.tag, error.thread, error.message)
                .filter { it.isNotBlank() }
                .joinToString(SEPARATOR)
        }

    private fun simpleClassName(qualified: String): String = qualified.substringAfterLast('.')

    private companion object {
        private const val SEPARATOR = " · "
    }
}
