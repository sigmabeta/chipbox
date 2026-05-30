package net.sigmabeta.chipbox.strings.real

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
    // Android Studio's Compose Preview renders under Layoutlib, whose classloader doesn't
    // expose the strings:real AAR's java-resources to `ClassLoader.getResourceAsStream` — so a
    // .cvr lookup throws `MissingResourceException` mid-composition. Compose forbids
    // try/catch around composable invocations, so we probe the classpath ONCE here and skip the
    // real-string path entirely when the resources aren't reachable. Paparazzi's JVM test
    // classpath does expose the .cvr files, so its snapshots still resolve real text; runtime
    // apps preload via [loadChipboxStrings] and never reach this function.
    val resourcesReachable = remember { hasComposeStringsOnClasspath() }
    if (!resourcesReachable) {
        return remember {
            val placeholders = mutableMapOf<SageStringId, String>()
            for (id in ChipboxStringId.entries) {
                placeholders[id] = id.name
            }
            ChipboxStringProvider(placeholders)
        }
    }

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

/** Single .cvr we probe before composition decides which provider to install. If this one isn't
 *  reachable via the classloader, none of the others are either — pick a stable file that has
 *  been around since the migration to Compose resources. */
private const val SENTINEL_RESOURCE_PATH =
    "composeResources/net.sigmabeta.chipbox.common.strings.real.generated.resources/" +
        "values/strings-accessibility.commonMain.cvr"

private fun hasComposeStringsOnClasspath(): Boolean =
    ClasspathResourceReader::class.java.classLoader
        ?.getResource(SENTINEL_RESOURCE_PATH) != null

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
