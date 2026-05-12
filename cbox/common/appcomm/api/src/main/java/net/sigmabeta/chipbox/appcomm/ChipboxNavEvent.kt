package net.sigmabeta.chipbox.appcomm

sealed class ChipboxNavEvent {
    data class NavigateTo(val destination: Any) : ChipboxNavEvent()
    data object NavigateBack : ChipboxNavEvent()
    data class OpenUrl(val url: String) : ChipboxNavEvent()
}
