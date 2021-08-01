package net.sigmabeta.chipbox.scanner.mock

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.scanner.Scanner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MockScannerModule {
    @Provides
    @Singleton
    fun provideMockScanner(): Scanner = MockScanner()
}