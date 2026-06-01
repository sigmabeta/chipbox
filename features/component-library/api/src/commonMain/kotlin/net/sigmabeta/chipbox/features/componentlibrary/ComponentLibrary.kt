package net.sigmabeta.chipbox.features.componentlibrary

import kotlinx.serialization.Serializable

/**
 * Debug-only gallery of the reusable Chipbox UI components, reached from the Settings debug section
 * (gated behind `shouldShowDebug`). This destination is the landing menu; picking one of its three
 * options pushes a [ComponentLibraryMode] sub-screen.
 */
@Serializable
data object ComponentLibrary
