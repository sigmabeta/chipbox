package net.sigmabeta.chipbox.di

import android.content.Context
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.crash.CrashReporter
import net.sigmabeta.chipbox.crash.CrashReportStore
import net.sigmabeta.chipbox.crash.real.RealCrashReporter
import net.sigmabeta.chipbox.crash.real.RealCrashReportStore
import net.sigmabeta.chipbox.strings.real.ChipboxStringProvider
import java.io.File
import net.sigmabeta.chipbox.strings.real.loadChipboxStrings
import net.sigmabeta.sage.analytics.Analytics
import net.sigmabeta.sage.analytics.NoopAnalytics
import net.sigmabeta.sage.android.logging.AndroidHatchet
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider
import okio.FileSystem

@BindingContainer
@ContributesTo(AppScope::class)
object AndroidAppModule {
    // Strings now come from the single multiplatform source (cbox/common/strings/real's
    // composeResources), preloaded once into an in-memory map. runBlocking is fine here: it's a
    // one-time app-scope construction reading bundled resources, off the UI critical path.
    @Provides
    @SingleIn(AppScope::class)
    fun provideStringProvider(): StringProvider = runBlocking { ChipboxStringProvider(loadChipboxStrings()) }

    @Provides
    @SingleIn(AppScope::class)
    fun provideHatchet(): Hatchet = AndroidHatchet()

    // Chipbox's Android build ships no real analytics backend — bind the no-op impl (now in
    // common:analytics). This replaces the former :fake:analytics module's contributed binding.
    @Provides
    @SingleIn(AppScope::class)
    fun provideAnalytics(hatchet: Hatchet): Analytics = NoopAnalytics(hatchet)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppInfo(): AppInfo = AppInfo(
        isDebug = BuildConfig.DEBUG,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildTimeMs = BuildConfig.BUILD_TIME_MS,
        buildBranch = BuildConfig.BUILD_BRANCH,
    )

    // Crash reports go under the app's private files dir (survives backgrounding, wiped on
    // uninstall/clear-data). Shared by the writer (CrashReporter) and the reader (CrashReportStore).
    @Provides
    @SingleIn(AppScope::class)
    @Named("crashDir")
    fun provideCrashDir(context: Context): File = context.filesDir.resolve("crashes")

    // Installed from ChipboxApplication.onCreate().
    @Provides
    @SingleIn(AppScope::class)
    fun provideCrashReporter(
        @Named("crashDir") crashDir: File,
        appInfo: AppInfo,
        hatchet: Hatchet,
    ): CrashReporter = RealCrashReporter(
        crashDir = crashDir,
        appInfo = appInfo,
        hatchet = hatchet,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideCrashReportStore(
        @Named("crashDir") crashDir: File,
        hatchet: Hatchet,
    ): CrashReportStore = RealCrashReportStore(crashDir = crashDir, hatchet = hatchet)

    // Both Android and JVM now resolve LibrarySource to the raw-path LocalFileContentSource (the
    // interface lives in cbox/common/contentsource/api; the impl is bound as a ContentSource in
    // AndroidFileContentSourceModule — this binding adds the LibrarySource face).
    @Provides
    @SingleIn(AppScope::class)
    fun provideLibrarySource(impl: LocalFileContentSource): LibrarySource = impl

    // FolderPicker's OkioFolderLister @Inject ctor takes a FileSystem; pin it to the live
    // platform filesystem. Other call sites that need a FileSystem (PCM cache, FileSpeaker,
    // …) currently pass FileSystem.SYSTEM inline in their @Provides — this is the first one
    // that injects it as a graph type.
    @Provides
    @SingleIn(AppScope::class)
    fun provideFileSystem(): FileSystem = FileSystem.SYSTEM
}
