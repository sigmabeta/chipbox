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

    @Component.Builder
    interface Builder {
        @BindsInstance fun dbPath(@Named("dbPath") path: String): Builder

        @BindsInstance fun workDir(@Named("workDir") dir: File): Builder

        @BindsInstance fun outputDir(@Named("outputDir") dir: File): Builder
        fun build(): JvmChipboxComponent
    }
}
