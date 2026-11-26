package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.ui.platform.testTag
import net.sigmabeta.sage.ui.composables.SageRenderers

/**
 * Chipbox's plug-in into SAGE's shared renderer suite.
 *
 * The built-in `ListModel` renderers now live in SAGE (`:common:ui:composables`); Chipbox contributes
 * only its own rows through [SageRenderers.contentFor]. SAGE's `ListModel.Content` entry point
 * consults this first, then falls through to the built-ins for every other model.
 *
 * SAGE tags only its own built-in rows with the model type; the two Chipbox rows tag themselves here
 * (the same `simpleName` tag the old `ComposableMapping` applied) so UI tests can still select them
 * by `hasTestTag("ScanStatusCardListModel")` / `hasTestTag("NowPlayingHomeCardListModel")`.
 */
val ChipboxRenderers = SageRenderers(
    contentFor = { model ->
        when (model) {
            is NowPlayingHomeCardListModel -> { _, sink, _, modifier, padding ->
                NowPlayingHomeCard(
                    model = model,
                    actionSink = sink,
                    modifier = modifier.testTag(model::class.simpleName ?: "Unknown"),
                    padding = padding,
                )
            }

            is ScanStatusCardListModel -> { _, sink, _, modifier, padding ->
                ScanStatusCard(
                    model = model,
                    actionSink = sink,
                    modifier = modifier.testTag(model::class.simpleName ?: "Unknown"),
                    padding = padding,
                )
            }

            else -> null
        }
    },
    previewImage = chipboxPreviewImage,
)
