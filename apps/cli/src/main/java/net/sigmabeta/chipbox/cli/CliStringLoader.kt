package net.sigmabeta.chipbox.cli

import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.real.ChipboxStringProvider
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.jar.JarFile

/**
 * Builds the CLI's [StringProvider] by reading Compose Resources' packaged `.cvr` files directly off
 * the classpath, instead of going through strings.real's `loadChipboxStrings()` -> Compose
 * `getString()`.
 *
 * Why bother: on the JVM, `getString()` decides which qualified variant to return by calling
 * `getSystemResourceEnvironment()`, which asks skiko for the system light/dark theme. That drags a
 * ~13 MB Skia native library (plus the whole compose-ui desktop stack) into a headless CLI to answer
 * a question with exactly one possible answer here — these strings have no `-night` or per-locale
 * variants. Parsing the `.cvr` text ourselves sidesteps Compose Resources entirely, so skiko and the
 * desktop Compose runtime can be excluded from the CLI classpath (see apps/cli/build.gradle.kts).
 *
 * `.cvr` format (UTF-8, one resource per line):
 *   version:0
 *   string[key]base64-of-the-UTF-8-value      (the real separator is a pipe character)
 * where the key is the lowercased [ChipboxStringId] name. This module emits only single-value
 * `string` rows — no plurals, arrays, or qualifier variants — so a line parser is sufficient.
 */
fun cliClasspathStringProvider(): StringProvider =
    ChipboxStringProvider(loadChipboxStringsFromCvr())

private const val VALUES_DIR =
    "composeResources/net.sigmabeta.chipbox.common.strings.real.generated.resources/values"

private fun loadChipboxStringsFromCvr(): Map<SageStringId, String> {
    val loader = CvrAnchor::class.java.classLoader
        ?: error("No classloader available to read packaged .cvr strings")

    val byKey = HashMap<String, String>()
    for (path in cvrResourcePaths(loader)) {
        val text = loader.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: continue
        parseCvr(text, byKey)
    }

    // Build a Map<SageStringId, String> explicitly: associateWith here would let the declared
    // SageStringId key type drive the element type to SageStringId (which has no `name`).
    val strings = mutableMapOf<SageStringId, String>()
    for (id in ChipboxStringId.entries) {
        val key = id.name.lowercase()
        strings[id] = byKey[key] ?: error("Missing string for ${id.name} (key '$key') in packaged .cvr files")
    }
    return strings
}

/** Each `.cvr` row is `type|key|base64`; `split(limit = CVR_FIELDS)` keeps any `|` in the value. */
private const val CVR_FIELDS = 3

private fun parseCvr(text: String, into: MutableMap<String, String>) {
    text.lineSequence()
        .map { it.split('|', limit = CVR_FIELDS) }
        .filter { it.size == CVR_FIELDS && it[0] == "string" }
        .forEach { into[it[1]] = String(Base64.getDecoder().decode(it[2]), Charsets.UTF_8) }
}

/**
 * Enumerate every `.cvr` file under [VALUES_DIR] on the classpath, handling both the installed-jar
 * case (`jar:` URLs) and the Gradle `run` / exploded-classes case (`file:` URLs).
 */
private fun cvrResourcePaths(loader: ClassLoader): List<String> =
    loader.getResources(VALUES_DIR).asSequence().flatMap(::cvrPathsForUrl).toList()

private fun cvrPathsForUrl(url: URL): List<String> = when (url.protocol) {
    "jar" -> jarCvrPaths((url.openConnection() as JarURLConnection).jarFile)
    "file" -> fileCvrPaths(url)
    else -> emptyList()
}

private fun jarCvrPaths(jar: JarFile): List<String> {
    val prefix = "$VALUES_DIR/"
    return jar.entries().asSequence()
        .map { it.name }
        .filter { it.startsWith(prefix) && it.endsWith(".cvr") }
        .toList()
}

private fun fileCvrPaths(url: URL): List<String> =
    Files.list(Path.of(url.toURI())).use { stream ->
        stream.map { it.fileName.toString() }
            .filter { it.endsWith(".cvr") }
            .map { "$VALUES_DIR/$it" }
            .toList()
    }

/** Anchor whose classloader we borrow to resolve packaged resources. */
private object CvrAnchor
