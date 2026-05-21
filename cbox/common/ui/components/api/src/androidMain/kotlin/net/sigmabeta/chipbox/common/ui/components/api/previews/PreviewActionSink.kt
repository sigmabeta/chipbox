package net.sigmabeta.chipbox.common.ui.components.api.previews

import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction

class PreviewActionSink(private val actionHandler: (SageAction) -> Unit = {}) : ActionSink {
    override fun sendAction(action: SageAction) {
        actionHandler(action)
    }
}
