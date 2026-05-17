package net.sigmabeta.chipbox.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.chipbox.strings.id
import net.sigmabeta.sage.android.logging.AndroidHatchet
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider
import net.sigmabeta.sage.ui.strings.AndroidStringProvider

@Module
@InstallIn(SingletonComponent::class)
object ChipboxModule {
    @Provides
    @Singleton
    fun provideStringProvider(@ApplicationContext context: Context): StringProvider = AndroidStringProvider(context.resources) { (it as ChipboxStringId).id() }

    @Provides
    @Singleton
    fun provideHatchet(): Hatchet = AndroidHatchet()

    @Provides
    @Singleton
    fun provideAppInfo(): AppInfo = AppInfo(
        isDebug = BuildConfig.DEBUG,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildTimeMs = BuildConfig.BUILD_TIME_MS,
        buildBranch = BuildConfig.BUILD_BRANCH,
    )
}
