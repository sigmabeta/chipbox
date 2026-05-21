package net.sigmabeta.chipbox.di

import android.content.Context
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.contentsource.AndroidFileContentSource
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.chipbox.strings.id
import net.sigmabeta.sage.android.logging.AndroidHatchet
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider
import net.sigmabeta.sage.ui.strings.AndroidStringProvider

@BindingContainer
@ContributesTo(AppScope::class)
object AndroidAppModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideStringProvider(context: Context): StringProvider = AndroidStringProvider(context.resources) { (it as ChipboxStringId).id() }

    @Provides
    @SingleIn(AppScope::class)
    fun provideHatchet(): Hatchet = AndroidHatchet()

    @Provides
    @SingleIn(AppScope::class)
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
    @SingleIn(AppScope::class)
    fun provideLibrarySource(impl: AndroidFileContentSource): LibrarySource = impl
}
