package net.sigmabeta.chipbox.features.library

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import net.sigmabeta.chipbox.ui.list.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@HiltViewModel
class LibraryViewModel @Inject constructor(
    stringProvider: StringProvider,
    private val hatchet: Hatchet,
) : ChipboxListViewModel<LibraryState>(LibraryState, stringProvider, hatchet) {

    override fun handleAction(action: SageAction) {
        when (action) {
            is LibraryAction -> hatchet.i("LibraryAction: $action  // TODO route to navigation")
            else -> Unit
        }
    }
}
