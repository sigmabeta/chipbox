package net.sigmabeta.chipbox.notif.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.chipbox.notif.NotifManager
import net.sigmabeta.chipbox.notif.ScanFailureNotifRecorder
import net.sigmabeta.chipbox.notif.real.RealNotifManager
import net.sigmabeta.chipbox.notif.real.RealScanFailureNotifRecorder
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.storage.common.Storage
import net.sigmabeta.sage.ui.StringProvider

@BindingContainer
@ContributesTo(AppScope::class)
object NotifModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideNotifManager(
        storage: Storage,
        scope: CoroutineScope,
        hatchet: Hatchet,
    ): NotifManager = RealNotifManager(storage, scope, hatchet)

    @Provides
    @SingleIn(AppScope::class)
    fun provideScanFailureNotifRecorder(
        scanner: Scanner,
        notifManager: NotifManager,
        stringProvider: StringProvider,
        hatchet: Hatchet,
    ): ScanFailureNotifRecorder = RealScanFailureNotifRecorder(
        scanner = scanner,
        notifManager = notifManager,
        stringProvider = stringProvider,
        hatchet = hatchet,
    )
}
