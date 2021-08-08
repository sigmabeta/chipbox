package net.sigmabeta.chipbox.scanner.real

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealScannerModule {
    @Provides
    @Singleton
    fun provideRealScanner(realRepository: Repository): Scanner = RealScanner(realRepository)
}