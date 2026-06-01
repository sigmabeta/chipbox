package net.sigmabeta.chipbox.features.componentlibrary

import kotlinx.serialization.Serializable

/**
 * Sub-screen of the component gallery showing every component in one [mode]. Pushed from the
 * [ComponentLibrary] menu; system back returns to it. One parameterized destination backs all
 * three modes — the [mode] is threaded into the ViewModel via assisted injection.
 */
@Serializable
data class ComponentLibraryMode(val mode: LibraryMode)
