package net.sigmabeta.chipbox.ui.platform

import net.sigmabeta.chipbox.model.objects.Platform
import net.sigmabeta.chipbox.ui.BaseView
import java.util.*

interface PlatformListView : BaseView {
    fun setList(list: ArrayList<Platform>)

    fun launchNavActivity(id: Long)
}
