package net.sigmabeta.chipbox.appcomm

sealed class ChipboxEvent {
    data class NavigateTo(val destination: Any) : ChipboxEvent()
    data object NavigateBack : ChipboxEvent()
    data class OpenUrl(val url: String) : ChipboxEvent()
    data class ShowSnackbar(
        val message: String,
        val withDismissAction: Boolean = true,
    ) : ChipboxEvent()
}
