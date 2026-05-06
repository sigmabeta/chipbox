package net.sigmabeta.chipbox.scanner.real

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.contentsource.AndroidFileContentSource
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealScannerModule {
    @Provides
    @Singleton
    fun provideReaders(hatchet: Hatchet): Readers = Readers(hatchet)

    @Provides
    @Singleton
    fun provideRealScanner(
        repository: Repository,
        contentSource: AndroidFileContentSource,
        readers: Readers,
        hatchet: Hatchet,
    ): Scanner = RealScanner(repository, contentSource, readers, hatchet)
}
