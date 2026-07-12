package net.sigmabeta.chipbox.features.folderpicker

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import net.sigmabeta.sage.di.AppScope
import java.io.File

/**
 * Android [StorageVolumeProvider]: enumerates mounted volumes via [StorageManager] so we never have
 * to list the traverse-only `/storage` chain (unreadable to apps even with All Files Access). API
 * 30+ reads each volume's root directory directly; below that the root is derived from the
 * app-specific dir on each volume, with the label from the matching volume when available.
 */
@Inject
@ContributesBinding(AppScope::class)
class AndroidStorageVolumeProvider(private val context: Context) : StorageVolumeProvider {
    override fun volumes(): List<StorageVolumeInfo> {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            ?: return emptyList()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storageManager.storageVolumes.mapNotNull { volume ->
                val root = volume.directory ?: return@mapNotNull null
                StorageVolumeInfo(label = volume.getDescription(context), path = root.absolutePath)
            }
        } else {
            context.getExternalFilesDirs(null).filterNotNull().mapNotNull { appDir ->
                val root = appDir.volumeRoot() ?: return@mapNotNull null
                val label = runCatching { storageManager.getStorageVolume(appDir)?.getDescription(context) }
                    .getOrNull()
                    ?: root.name
                StorageVolumeInfo(label = label, path = root.absolutePath)
            }
        }
    }

    /** `/storage/XXXX/Android/data/<pkg>/files` → `/storage/XXXX`; null if the marker isn't present. */
    private fun File.volumeRoot(): File? {
        val marker = "/Android/data/"
        val index = absolutePath.indexOf(marker)
        return if (index > 0) File(absolutePath.substring(0, index)) else null
    }
}
