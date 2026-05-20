package net.sigmabeta.chipbox.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import javax.inject.Named
import javax.inject.Singleton
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.contentsource.AndroidFileContentSource
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatus
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatusEntryPoint
import net.sigmabeta.chipbox.features.settings.SettingsViewModel
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.chipbox.strings.id
import net.sigmabeta.sage.android.logging.AndroidHatchet
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider
import net.sigmabeta.sage.ui.strings.AndroidStringProvider

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object AndroidAppModule {
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

    // SettingsViewModel's LibrarySource param resolves to the SAF-backed Android impl on this
    // target. (The interface lives in cbox/common/contentsource/api; AndroidFileContentSource
    // is bound as a ContentSource elsewhere — this binding adds the LibrarySource face.)
    @Provides
    @Singleton
    fun provideLibrarySource(impl: AndroidFileContentSource): LibrarySource = impl

    // SettingsViewModel's playback-status flag/destination — Android wires the real entry
    // point's availability and routes clicks to the PlaybackStatus screen. The JVM target's
    // JvmChipboxComponent provides constant false + null for the same @Named keys.
    @Provides
    @Named(SettingsViewModel.PLAYBACK_STATUS_AVAILABLE)
    fun providePlaybackStatusAvailable(entryPoint: PlaybackStatusEntryPoint): Boolean =
        entryPoint.isAvailable

    @Provides
    @Named(SettingsViewModel.PLAYBACK_STATUS_DESTINATION)
    fun providePlaybackStatusDestination(): Any = PlaybackStatus
}
