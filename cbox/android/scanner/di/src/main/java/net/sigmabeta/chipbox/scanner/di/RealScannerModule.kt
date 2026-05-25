package net.sigmabeta.chipbox.scanner.real

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.contentsource.AndroidFileContentSource
import net.sigmabeta.chipbox.player.emulators.vgmstream.VgmstreamProbe
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@BindingContainer
@ContributesTo(AppScope::class)
object RealScannerModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideReaders(hatchet: Hatchet): Readers = Readers(hatchet)

    @Provides
    @SingleIn(AppScope::class)
    fun provideRealScanner(
        repository: Repository,
        contentSource: AndroidFileContentSource,
        readers: Readers,
        hatchet: Hatchet,
    ): Scanner = RealScanner(repository, contentSource, readers, VgmstreamProbe, hatchet)
}
