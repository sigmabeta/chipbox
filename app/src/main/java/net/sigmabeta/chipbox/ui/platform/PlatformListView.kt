package net.sigmabeta.chipbox.ui.platform

import io.realm.OrderedCollectionChangeSet
import net.sigmabeta.chipbox.model.domain.Platform
import net.sigmabeta.chipbox.ui.BaseView

interface PlatformListView : BaseView {
    fun setList(list: List<Platform>)

    fun animateChanges(it: OrderedCollectionChangeSet)

    fun launchNavActivity(id: String)
}
