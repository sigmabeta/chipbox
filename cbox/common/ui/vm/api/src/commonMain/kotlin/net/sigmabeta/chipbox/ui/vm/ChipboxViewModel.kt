package net.sigmabeta.chipbox.ui.vm

/**
 * Marker base class for Chipbox UI view-models. Deliberately not
 * `androidx.lifecycle.ViewModel` (yet) — the KMP lifecycle artifact's `onCleared` / scope
 * shape lands when the first real Android screen ports to this module and actually needs
 * lifecycle hooks. For now the only consumer is the desktop demo composable.
 */
open class ChipboxViewModel
