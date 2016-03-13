package net.sigmabeta.chipbox.view.interfaces

import net.sigmabeta.chipbox.model.objects.Platform
import java.util.*

interface PlatformListView : BaseView {
    fun setList(list: ArrayList<Platform>)

    fun launchNavActivity(id: Long)
}
