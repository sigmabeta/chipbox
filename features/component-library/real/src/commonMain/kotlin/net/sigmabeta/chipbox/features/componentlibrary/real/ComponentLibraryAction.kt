package net.sigmabeta.chipbox.features.componentlibrary.real

import net.sigmabeta.chipbox.appcomm.ChipboxAction
import net.sigmabeta.chipbox.features.componentlibrary.LibraryMode

internal sealed class ComponentLibraryAction : ChipboxAction() {
    data class OpenMode(val mode: LibraryMode) : ComponentLibraryAction()
}
