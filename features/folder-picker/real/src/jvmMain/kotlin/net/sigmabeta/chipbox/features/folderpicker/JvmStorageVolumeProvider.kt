package net.sigmabeta.chipbox.features.folderpicker

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import net.sigmabeta.sage.di.AppScope

/**
 * Desktop [StorageVolumeProvider]: no storage-volume chooser — the filesystem is navigable up to "/"
 * directly, so there's nothing to enumerate.
 */
@Inject
@ContributesBinding(AppScope::class)
class JvmStorageVolumeProvider : StorageVolumeProvider {
    override fun volumes(): List<StorageVolumeInfo> = emptyList()
}
