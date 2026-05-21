package net.sigmabeta.chipbox.ui.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.api.id
import net.sigmabeta.chipbox.ui.components.api.Content
import net.sigmabeta.chipbox.ui.theme.api.AppTheme
import net.sigmabeta.sage.android.perf.DURATION_THRESHOLD_ERROR_SCREEN_PREVIEW
import net.sigmabeta.sage.android.perf.DURATION_THRESHOLD_WARNING_SCREEN_PREVIEW
import net.sigmabeta.sage.android.perf.LocalLogger
import net.sigmabeta.sage.android.perf.WithMeasurementScreen
import net.sigmabeta.sage.android.ui.list.GridScreen
import net.sigmabeta.sage.android.ui.list.ListScreen
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.list.ListStateActual
import net.sigmabeta.sage.list.WidthClass
import net.sigmabeta.sage.logging.BasicHatchet
import net.sigmabeta.sage.ui.StringProvider
import net.sigmabeta.sage.ui.strings.AndroidStringProvider

/**
 * Renders a screen's [ListState] through the real SAGE list pipeline, exactly as the app's
 * navigation entry would, but with no ViewModels/Hilt so it is Paparazzi- and `@Preview`-safe.
 *
 * Chipbox's full chrome ([net.sigmabeta.chipbox.common.appui.api.ChipboxAppUi]) is ViewModel-bound, so the
 * preview uses a lightweight VM-free [PreviewChrome] (top bar driven by the rendered title).
 */
@Composable
fun ListScreenPreview(
    screenState: ListState,
    darkTheme: Boolean,
    syntheticWidthClass: WidthClass,
) {
    val actionSink = ActionSink { }
    val stringProvider =
        AndroidStringProvider(LocalContext.current.resources) { (it as ChipboxStringId).id() }
    val state = screenState.toActual(stringProvider)

    AppTheme(forceDark = darkTheme) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalLogger provides BasicHatchet(),
        ) {
            PreviewChrome(titleBarModel = state.title) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                        .fillMaxSize(),
                ) {
                    WithMeasurementScreen(
                        "${state.title.title ?: "Unknown"} ($syntheticWidthClass)",
                        DURATION_THRESHOLD_WARNING_SCREEN_PREVIEW,
                        DURATION_THRESHOLD_ERROR_SCREEN_PREVIEW,
                    ) {
                        ListContent(state, syntheticWidthClass, actionSink)
                    }
                }
            }
        }
    }
}

/**
 * For screens that own their chrome and don't render through the generic list pipeline (e.g.
 * Search, with its in-screen SearchBar). Provides the same theme / `LocalInspectionMode` /
 * logger wrapper as [ListScreenPreview] but no top bar, and hands the [content] a
 * [StringProvider] so it can turn a screen state into list items itself.
 */
@Composable
fun ScreenPreview(
    darkTheme: Boolean,
    syntheticWidthClass: WidthClass,
    screenName: String = "Screen",
    content: @Composable (StringProvider) -> Unit,
) {
    val stringProvider =
        AndroidStringProvider(LocalContext.current.resources) { (it as ChipboxStringId).id() }

    AppTheme(forceDark = darkTheme) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalLogger provides BasicHatchet(),
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                WithMeasurementScreen(
                    "$screenName ($syntheticWidthClass)",
                    DURATION_THRESHOLD_WARNING_SCREEN_PREVIEW,
                    DURATION_THRESHOLD_ERROR_SCREEN_PREVIEW,
                ) {
                    content(stringProvider)
                }
            }
        }
    }
}

/**
 * Roughly equivalent to `ChipboxListEntry` — picks [GridScreen]/[ListScreen] the same way the
 * real list nav entry does.
 */
@Composable
private fun ListContent(
    state: ListStateActual,
    displayWidthClass: WidthClass,
    actionSink: ActionSink,
) {
    val columnType = state.columnType
    val numColumns = columnType.numberOfColumns(displayWidthClass)

    require(numColumns > 0) {
        "Calculated number of columns is zero for ${state.columnType} and $displayWidthClass."
    }

    val sideMargin = 16.dp
    val itemContent: @Composable (ListModel, ActionSink, Boolean, Modifier, PaddingValues) -> Unit =
        { model, sink, debug, mod, pad -> model.Content(sink, debug, mod, pad) }

    if (numColumns > 1) {
        val (staggered, allowHorizScroller) = if (columnType is ColumnType.Staggered) {
            true to columnType.allowHorizScroller
        } else {
            false to false
        }

        GridScreen(
            state = state,
            actionSink = actionSink,
            showDebug = false,
            numberOfColumns = numColumns,
            staggered = staggered,
            allowHorizScroller = allowHorizScroller,
            sideMargin = sideMargin,
            modifier = Modifier,
            itemContent = itemContent,
        )
    } else {
        ListScreen(
            state = state,
            actionSink = actionSink,
            showDebug = false,
            sideMargin = sideMargin,
            modifier = Modifier,
            itemContent = itemContent,
        )
    }
}

/**
 * VM-free stand-in for [net.sigmabeta.chipbox.common.appui.api.ChipboxAppUi]'s scaffold: a Material3 top
 * bar showing the screen's title. Nav rail/bar and the player overlay are intentionally omitted
 * because they require Hilt ViewModels and are not what these screenshots regression-test.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewChrome(
    titleBarModel: TitleBarModel,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = titleBarModel.title.orEmpty()) })
        },
        content = { innerPadding -> content(innerPadding) },
    )
}

/**
 * Mirrors the private `rememberWidthClass` in `ChipboxListEntry`; used as the `@Preview` default
 * so the IDE gutter renders a sensible layout.
 */
@Composable
fun previewWidthClass(): WidthClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < WIDTH_BREAKPOINT_MEDIUM -> WidthClass.COMPACT
        widthDp < WIDTH_BREAKPOINT_EXPANDED -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
}

private const val WIDTH_BREAKPOINT_MEDIUM = 600
private const val WIDTH_BREAKPOINT_EXPANDED = 840
