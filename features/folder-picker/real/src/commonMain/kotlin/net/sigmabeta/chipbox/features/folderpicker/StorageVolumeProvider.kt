package net.sigmabeta.chipbox.features.folderpicker

/**
 * Supplies the device's mounted storage volumes ([StorageVolumeInfo]) to the picker. The platform
 * binding decides *how* — Android reports them via `StorageManager` (so we never enumerate the
 * traverse-only `/storage` chain apps can't read); desktop has no volume concept and returns empty.
 *
 * A `fun interface` so tests can pass a lambda and production binds a `@ContributesBinding` impl per
 * platform. When it returns more than one volume the picker opens on the volume chooser; a single
 * (or no) volume drops straight into the per-OS default folder.
 */
fun interface StorageVolumeProvider {
    fun volumes(): List<StorageVolumeInfo>
}
