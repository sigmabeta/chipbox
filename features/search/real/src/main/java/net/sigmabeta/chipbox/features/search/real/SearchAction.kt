package net.sigmabeta.chipbox.features.search.real

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class SearchAction : ChipboxAction() {
    // Clearing the query is handled locally (textFieldUpdater("")) since the query is
    // hoisted state, not ViewModel state — so the only action the VM needs is back.
    data object BackClicked : SearchAction()
}
