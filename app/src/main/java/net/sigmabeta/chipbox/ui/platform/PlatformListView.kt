package net.sigmabeta.chipbox.ui.platform

import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.ui.ListView

interface PlatformListView : ListView<Platform, PlatformViewHolder> {
    fun launchNavActivity(platformName: String)
}
