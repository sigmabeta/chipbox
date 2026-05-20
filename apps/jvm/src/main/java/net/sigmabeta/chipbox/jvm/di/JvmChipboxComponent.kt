package net.sigmabeta.chipbox.jvm.di

import dagger.BindsInstance
import dagger.Component
import net.sigmabeta.chipbox.jvm.HelloViewModel
import net.sigmabeta.chipbox.jvm.LocalFileContentSource
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.player.speaker.file.FileSpeaker
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

/**
 * Plain-Dagger graph for the headless JVM target — the JVM equivalent of the Android app's
 * Hilt graph. Each `@Module` in the list mirrors a Hilt `@Module` from `cbox/.../di` but is
 * declared in [JvmModules.kt] because the JVM has different wiring needs (bundled SQLite
 * driver instead of Context-backed Room, file walker instead of SAF, CLI-supplied output dir
 * instead of `Environment.getExternalStorageDirectory()`).
 *
 * Caller-supplied paths come in via [Builder.dbPath] / [Builder.workDir] /
 * [Builder.outputDir]. The component is built once in `Main.kt` and pulled per mode.
 */
@Singleton
@Component(
    modules = [
        HatchetModule::class,
        JvmStringsModule::class,
        JvmAppInfoModule::class,
        JvmStorageModule::class,
        JvmSettingsManagersModule::class,
        JvmDatabaseModule::class,
        JvmRepositoryModule::class,
        JvmContentSourceModule::class,
        JvmBufferModule::class,
        JvmEmulatorsModule::class,
        JvmReadersModule::class,
        JvmScannerModule::class,
        JvmGeneratorModule::class,
        JvmSpeakerModule::class,
    ]
)
interface JvmChipboxComponent {

    fun hatchet(): Hatchet
    fun stringProvider(): StringProvider
    fun repository(): Repository
    fun librarySource(): LocalFileContentSource
    fun scanner(): RealScanner
    fun generator(): RealGenerator
    fun speaker(): FileSpeaker

    // Demo view-model surfaced for the Compose Multiplatform desktop bootstrap. Pulled by
    // JvmViewModelProvider's `when` arm for HelloViewModel; future feature ports surface
    // their own VMs here (or migrate to a Dagger Multibindings map keyed by KClass once
    // there's more than one).
    fun helloViewModel(): HelloViewModel

    // Uses @Component.Factory rather than @Component.Builder so the caller-supplied paths
    // arrive as factory params with `@BindsInstance` on the params themselves. The Builder
    // form would put `@BindsInstance` on abstract methods, and Metro's Dagger-interop
    // (running alongside Dagger during the M4c slice — see docs/metro-migration.md) reads
    // those as `@Provides` declarations and rejects them as body-less. Param-level
    // `@BindsInstance` keeps Dagger happy without tripping the Metro check.
    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance @Named("dbPath") dbPath: String,
            @BindsInstance @Named("workDir") workDir: File,
            @BindsInstance @Named("outputDir") outputDir: File,
        ): JvmChipboxComponent
    }
}
