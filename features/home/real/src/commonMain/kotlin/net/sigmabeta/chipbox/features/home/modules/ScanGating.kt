package net.sigmabeta.chipbox.features.home.modules

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.appcomm.LCE

/**
 * Gate a Home module's content on library-scan state. While a scan is in flight the section stays
 * hidden ([LCE.Uninitialized]) rather than surfacing a moving, partial library — the reactive
 * repository queries these rows lean on re-emit on every insert/update, so an ungated row visibly
 * churns as the scan discovers content. Once the scan settles the real [content] flow takes over.
 *
 * [distinctUntilChanged] on the scanning flag keeps us from re-subscribing to [content] on every
 * `Scanning` progress emission; the library only changes via a scan, so the scan-settled
 * transition is exactly when content is worth (re)reading. [content] is a factory so each settle
 * gets a fresh subscription.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Scanner.hideSectionWhileScanning(
    content: () -> Flow<LCE<HomeModuleSection>>,
): Flow<LCE<HomeModuleSection>> = state()
    .map { it is ScannerState.Scanning }
    .distinctUntilChanged()
    .flatMapLatest { scanning ->
        if (scanning) flowOf(LCE.Uninitialized) else content()
    }
