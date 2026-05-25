package net.sigmabeta.chipbox.features.browsebyplatform

import net.sigmabeta.chipbox.appcomm.ChipboxAction
import net.sigmabeta.chipbox.models.Platform

sealed class BrowseByPlatformAction : ChipboxAction() {
    data class PlatformClicked(val platform: Platform) : BrowseByPlatformAction()
}
