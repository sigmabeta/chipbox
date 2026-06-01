package net.sigmabeta.chipbox.features.componentlibrary

import kotlinx.serialization.Serializable

/**
 * Which layout the component gallery renders.
 *
 * - [LIST]: full-width single column (includes a horizontal scroller of grid items).
 * - [GRID]: a multi-column grid of grid items, with the odd full-width item mixed in.
 * - [COLUMNS]: the app's detail-screen staggered layout (one column on phones, two when wider).
 */
@Serializable
enum class LibraryMode { LIST, GRID, COLUMNS }
