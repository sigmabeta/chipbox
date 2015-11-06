package net.sigmabeta.chipbox.view.interfaces

import net.sigmabeta.chipbox.model.objects.Platform
import java.util.*

interface PlatformListView {
    fun onItemClick(id: Long)

    fun setList(list: ArrayList<Platform>)
}
