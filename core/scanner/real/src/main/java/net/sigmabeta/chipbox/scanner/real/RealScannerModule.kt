package net.sigmabeta.chipbox.scanner.real

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealScannerModule {
    @Provides
    @Singleton
    fun provideRealScanner(
        realRepository: Repository,
        @ApplicationContext context: Context
    ): Scanner = RealScanner(
        realRepository,
        context
    )
}