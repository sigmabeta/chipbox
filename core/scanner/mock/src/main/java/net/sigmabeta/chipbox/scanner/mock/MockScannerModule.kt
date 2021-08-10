package net.sigmabeta.chipbox.scanner.mock

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object MockScannerModule {
//    @Provides
//    @Singleton
//    fun provideMockScanner(mockRepository: MockRepository): Scanner = MockScanner(mockRepository)
}