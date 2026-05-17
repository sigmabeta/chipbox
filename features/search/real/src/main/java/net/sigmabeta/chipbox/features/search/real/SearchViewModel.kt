package net.sigmabeta.chipbox.features.search.real

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.freeform.ChipboxFreeformViewModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@HiltViewModel
class SearchViewModel @Inject constructor(
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxFreeformViewModel<SearchState, SearchModel>(
    SearchState(),
    stringProvider,
    hatchet,
) {
    override fun handleAction(action: SageAction) {
        when (action) {
            SearchAction.BackClicked -> emit(ChipboxEvent.NavigateBack)
        }
    }
}
