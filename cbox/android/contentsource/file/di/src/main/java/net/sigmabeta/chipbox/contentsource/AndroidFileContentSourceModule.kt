package net.sigmabeta.chipbox.contentsource

import android.content.Context
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.sage.di.AppScope
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

@ContributesTo(AppScope::class)
interface AndroidFileContentSourceModule {

    // Scope intentionally not present on this @Binds — Metro rejects scopes on @Binds.
    // The provideLocalFileContentSource() @Provides below is @SingleIn(AppScope::class),
    // so the underlying instance is still scoped; this @Binds just aliases it into the
    // multibinding Set<ContentSource>.
    @Binds
    @IntoSet
    fun bindAsContentSource(impl: LocalFileContentSource): ContentSource

    @Multibinds
    fun contentSources(): Set<@JvmSuppressWildcards ContentSource>

    companion object {
        // Android dropped SAF in favour of MANAGE_EXTERNAL_STORAGE + raw paths, so it shares the
        // JVM's LocalFileContentSource. Persist the library-location list in the app's private
        // filesDir (the JVM app uses its workDir).
        @Provides
        @SingleIn(AppScope::class)
        fun provideLocalFileContentSource(context: Context, fileSystem: FileSystem): LocalFileContentSource =
            LocalFileContentSource(fileSystem, File(context.filesDir, "library-locations.txt").toOkioPath())

        // ContentSourceRegistry is a plain (framework-free) type in contentsource:api; build it
        // from the multibinding Set<ContentSource> here. The JVM app wires its own equivalent in
        // JvmModules.
        @Provides
        @SingleIn(AppScope::class)
        fun provideContentSourceRegistry(
            sources: Set<@JvmSuppressWildcards ContentSource>,
        ): ContentSourceRegistry = ContentSourceRegistry(sources)
    }
}
