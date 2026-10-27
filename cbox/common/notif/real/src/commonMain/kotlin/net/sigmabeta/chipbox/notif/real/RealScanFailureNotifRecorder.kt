package net.sigmabeta.chipbox.notif.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.notif.Notif
import net.sigmabeta.chipbox.notif.NotifCategory
import net.sigmabeta.chipbox.notif.NotifManager
import net.sigmabeta.chipbox.notif.ScanFailureNotifRecorder
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.state.ScanFailureKind
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/**
 * Records a persisted error notification whenever the library scanner enters
 * [ScannerState.Failed]. One notification id is reused, so a later failure replaces the earlier
 * one instead of stacking rows. It's a one-time notice: the next launch clears it.
 */
class RealScanFailureNotifRecorder(
    private val scanner: Scanner,
    private val notifManager: NotifManager,
    private val stringProvider: StringProvider,
    private val hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ScanFailureNotifRecorder {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var observing = false

    override fun observe() {
        if (observing) return
        observing = true

        scope.launch {
            scanner.state()
                .filterIsInstance<ScannerState.Failed>()
                .distinctUntilChanged()
                .collect { failed ->
                    hatchet.w("Scan failed; posting notification for ${failed.path}")
                    notifManager.addNotif(
                        Notif(
                            id = NOTIF_ID,
                            title = ChipboxStringId.NOTIF_SCAN_FAILED_TITLE,
                            description = if (failed.kind == ScanFailureKind.CONFIGURATION) {
                                // No failing file to name; describe the admin-fixable config problem.
                                stringProvider.getString(
                                    ChipboxStringId.NOTIF_SCAN_FAILED_CONFIGURATION_DESCRIPTION,
                                )
                            } else {
                                stringProvider.getStringOneArg(
                                    ChipboxStringId.HOME_SCAN_STATUS_ERROR,
                                    failed.path,
                                )
                            },
                            category = NotifCategory.ERROR,
                            isOneTime = true,
                        ),
                    )
                }
        }
    }

    /** Cancel the collector. Production leaves it running for the process's life; tests use this. */
    fun release() {
        scope.cancel()
    }

    companion object {
        // String.hashCode is stable across processes (unlike enum identity hashCodes).
        val NOTIF_ID: Long = "notif.scan_failed".hashCode().toLong()
    }
}
