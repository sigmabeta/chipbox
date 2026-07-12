package net.sigmabeta.chipbox.features.folderpicker

/**
 * A mounted storage volume the picker can jump between: [label] is the human-readable name
 * ("Internal shared storage", "SD card", ...) and [path] is the volume's root directory
 * (`/storage/emulated/0`, `/storage/1A2B-3C4D`, ...).
 *
 * The list is gathered per-platform and handed to [FolderPickerViewModel] as a factory argument —
 * on Android via `StorageManager` (which reports volumes without needing to enumerate the
 * traverse-only `/storage` chain), and empty everywhere else. When non-empty, "Go up a folder" from
 * a volume root opens a synthetic chooser of these volumes rather than the unreadable filesystem
 * parent above it.
 */
data class StorageVolumeInfo(
    val label: String,
    val path: String,
)
