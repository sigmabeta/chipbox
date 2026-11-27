package net.sigmabeta.chipbox.common.ui.components.api

import net.sigmabeta.sage.ui.composables.SageRenderers

/**
 * Chipbox's plug-in into SAGE's shared renderer suite.
 *
 * The built-in `ListModel` renderers now live in SAGE (`:common:ui:composables`); Chipbox contributes
 * only its own rows through [SageRenderers.contentFor]. SAGE's `ListModel.Content` entry point tags
 * every row with its model type and wraps it in the perf measurement, then consults this and falls
 * through to the built-ins for every other model.
 */
val ChipboxRenderers = SageRenderers(
    contentFor = { model ->
        when (model) {
            is NowPlayingHomeCardListModel -> { _, sink, _, modifier, padding ->
                NowPlayingHomeCard(model = model, actionSink = sink, modifier = modifier, padding = padding)
            }

            is ScanStatusCardListModel -> { _, sink, _, modifier, padding ->
                ScanStatusCard(model = model, actionSink = sink, modifier = modifier, padding = padding)
            }

            else -> null
        }
    },
    previewImage = chipboxPreviewImage,
)
