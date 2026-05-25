package net.sigmabeta.chipbox.strings.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import net.sigmabeta.chipbox.common.strings.real.generated.resources.Res
import net.sigmabeta.chipbox.common.strings.real.generated.resources.allStringResources
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.LocalResourceReader
import org.jetbrains.compose.resources.MissingResourceException
import org.jetbrains.compose.resources.ResourceReader
import org.jetbrains.compose.resources.stringResource
import java.io.InputStream

/**
 * Compose-side [ChipboxStringProvider] for `@Preview` / Paparazzi (Android only). Reads each string
 * through the `@Composable` `stringResource` API — the same Compose-resource source the apps use —
 * so previews resolve real text without a `suspend`/`runBlocking` preload, and the recorded goldens
 * stay byte-identical (the values are unchanged).
 *
 * Under Paparazzi the auto-init `ContentProvider` that hands Compose's default Android resource
 * reader a `Context` never runs (and Paparazzi's own `Context.assets` is unusable), so that reader
 * throws. We sidestep it by providing [LocalResourceReader] — the documented test override hook that
 * `stringResource` consults — with a reader that pulls the packaged `.cvr` files straight off the
 * classpath, which is exactly where they live on the unit-test runtime. The running apps never reach
 * here; they preload via [loadChipboxStrings].
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberChipboxStringProvider(): StringProvider {
    lateinit var provider: StringProvider
    CompositionLocalProvider(LocalResourceReader provides ClasspathResourceReader) {
        val strings = mutableMapOf<SageStringId, String>()
        for (id in ChipboxStringId.entries) {
            strings[id] = stringResource(Res.allStringResources.getValue(id.name.lowercase()))
        }
        provider = ChipboxStringProvider(strings)
    }
    return provider
}

/**
 * A [ResourceReader] that reads Compose resources purely from the JVM classpath, with no Android
 * `Context` — the form the packaged `composeResources` `.cvr` files take on the Paparazzi and
 * unit-test runtime classpath.
 */
@OptIn(ExperimentalResourceApi::class)
private object ClasspathResourceReader : ResourceReader {
    override suspend fun read(path: String): ByteArray = open(path).use { it.readBytes() }

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray =
        open(path).use { input ->
            var skipped = 0L
            while (skipped < offset) {
                val count = input.skip(offset - skipped)
                if (count <= 0L) break
                skipped += count
            }
            val result = ByteArray(size.toInt())
            var read = 0
            while (read < result.size) {
                val count = input.read(result, read, result.size - read)
                if (count <= 0) break
                read += count
            }
            result
        }

    override fun getUri(path: String): String =
        loader().getResource(path)?.toURI()?.toString() ?: throw MissingResourceException(path)

    private fun open(path: String): InputStream =
        loader().getResourceAsStream(path) ?: throw MissingResourceException(path)

    private fun loader(): ClassLoader =
        javaClass.classLoader ?: error("No classloader available for Compose resource reading")
}
