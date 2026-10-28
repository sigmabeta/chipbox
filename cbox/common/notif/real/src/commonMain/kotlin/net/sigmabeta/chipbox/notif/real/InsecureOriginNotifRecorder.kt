package net.sigmabeta.chipbox.notif.real

import net.sigmabeta.chipbox.notif.Notif
import net.sigmabeta.chipbox.notif.NotifCategory
import net.sigmabeta.chipbox.notif.NotifManager
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/**
 * Posts an error notification when the app runs on an origin the browser does not consider secure.
 *
 * Browser audio uses an `AudioWorklet`, which is a secure-context-only API: on plain-HTTP LAN
 * deployments `AudioContext.audioWorklet` is `undefined`, so playback can never start even though
 * browsing and render-ahead caching work. The web target supplies the browser's
 * `window.isSecureContext` check; every other platform leaves the default (`true`) and never posts.
 *
 * One id is reused, so repeated calls replace the row rather than stacking. It is a one-time
 * notice: the next launch drops it, and a launch on a secure origin never adds it back.
 */
class InsecureOriginNotifRecorder(
    private val notifManager: NotifManager,
    private val stringProvider: StringProvider,
    private val hatchet: Hatchet,
    private val isSecureContext: () -> Boolean = { true },
) {

    fun observe() {
        if (isSecureContext()) return

        hatchet.w("Insecure origin; audio playback is unavailable without HTTPS or localhost.")
        notifManager.addNotif(
            Notif(
                id = NOTIF_ID,
                title = ChipboxStringId.NOTIF_INSECURE_ORIGIN_TITLE,
                description = stringProvider.getString(ChipboxStringId.NOTIF_INSECURE_ORIGIN_DESCRIPTION),
                category = NotifCategory.ERROR,
                isOneTime = true,
            ),
        )
    }

    companion object {
        // String.hashCode is stable across processes (unlike enum identity hashCodes).
        val NOTIF_ID: Long = "notif.insecure_origin".hashCode().toLong()
    }
}
