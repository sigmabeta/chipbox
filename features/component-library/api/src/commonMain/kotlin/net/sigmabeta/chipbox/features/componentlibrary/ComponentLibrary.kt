package net.sigmabeta.chipbox.features.componentlibrary

import kotlinx.serialization.Serializable

/**
 * Debug-only component gallery, reached from the Settings debug section (gated behind
 * `shouldShowDebug`). One screen: SAGE's component library switches list, grid and columns in place,
 * with Chipbox's own cards appended.
 */
@Serializable
data object ComponentLibrary
