package net.sigmabeta.chipbox.common.ui.components.api.subs

/**
 * On the web target image references are already URLs served by the backend, so they pass straight
 * through to Coil unchanged.
 */
internal actual fun platformImageModel(info: Any?): Any? = info
