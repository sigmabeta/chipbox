package net.sigmabeta.chipbox.uitest.harness

import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import net.sigmabeta.sage.logging.Hatchet
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * On a failed UI test, dump two diagnostics from the live scene before the failure propagates:
 *
 *  - a **semantics-tree dump** (`onRoot().printToString()`) — usually the most useful, since the
 *    common failure is "a matcher found nothing"; the dump shows the nodes that *were* present.
 *  - a **PNG screenshot** of the root — "what it looked like", encoded cross-platform.
 *
 * This is failure-only and never *compares* anything, so it dodges the determinism/cross-platform
 * pixel issues that make golden-image diffing fragile here (see docs/architecture/ui-test-dsl.md).
 *
 * Everything is best-effort and wrapped so a capture problem can never mask the real failure. Paths
 * are logged through [hatchet] (a real [net.sigmabeta.sage.logging.BasicHatchet], so they show up in
 * test output / logcat).
 *
 * Artifact location is resolved by [platformArtifactDir] first (per-target), then a shared default:
 *  - **desktop (`jvmTest`)** — the `jvmTest` task sets `chipbox.uitest.artifactDir` to the module's
 *    `build/uitest-failures` (discoverable, CI-collectable); else `<java.io.tmpdir>/chipbox-uitest`.
 *  - **device (`androidDeviceTest`)** — AGP's `additionalTestOutputDir` (pulled back to the host's
 *    `build/outputs/connected_android_test_additional_output/...`), else the app's external files dir.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.writeFailureArtifacts(failure: Throwable, hatchet: Hatchet) {
    runCatching {
        val testName = currentTestName()
        val dir = artifactDir().apply { mkdirs() }

        val semantics = runCatching { onRoot(useUnmergedTree = false).printToString(Int.MAX_VALUE) }
            .getOrElse { "Could not dump semantics tree: ${it.message}" }
        val semanticsFile = File(dir, "$testName-semantics.txt").apply {
            writeText(
                buildString {
                    appendLine("Test:    $testName")
                    appendLine("Failure: ${failure::class.simpleName}: ${failure.message}")
                    appendLine()
                    append(semantics)
                },
            )
        }

        val screenshot = runCatching {
            val png = encodePng(onRoot().captureToImage().toPixelMap())
            File(dir, "$testName.png").apply { writeBytes(png) }.absolutePath
        }.getOrElse { "(screenshot capture failed: ${it.message})" }

        hatchet.e(
            "UI test '$testName' failed — wrote artifacts:\n" +
                "  screenshot: $screenshot\n" +
                "  semantics:  ${semanticsFile.absolutePath}",
        )
    }
}

/** The failing @Test method, scraped off the call stack — the first frame outside the harness and
 *  the test/coroutine framework. Sanitized for use as a filename. */
private fun currentTestName(): String {
    val frame = Thread.currentThread().stackTrace.firstOrNull { element ->
        val className = element.className
        FRAMEWORK_PREFIXES.none { className.startsWith(it) }
    }
    val raw = frame?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" } ?: "ui-test"
    return raw.replace(FILENAME_UNSAFE, "_")
}

private fun artifactDir(): File {
    // On-device this returns AGP's pulled-back output dir; on desktop it's null (use the default).
    platformArtifactDir()?.let { return it }
    val override = System.getProperty("chipbox.uitest.artifactDir")
    return if (override != null) {
        File(override)
    } else {
        File(System.getProperty("java.io.tmpdir"), "chipbox-uitest")
    }
}

/**
 * Encode an [PixelMap] to a PNG byte array using only `java.util.zip` + `java.io` — no
 * `toAwtImage`/`asAndroidBitmap`, so the *same* code compiles and runs on both the desktop JVM
 * (`jvmTest`) and an Android device (`androidDeviceTest`). 8-bit truecolour + alpha (colour type 6),
 * one IDAT, "none" scanline filter — minimal but valid.
 */
internal fun encodePng(pixels: PixelMap): ByteArray {
    val width = pixels.width
    val height = pixels.height

    // Raw image: each scanline is a filter byte (0 = none) followed by RGBA bytes per pixel.
    val raw = ByteArray((width * BYTES_PER_PIXEL + 1) * height)
    var pos = 0
    for (y in 0 until height) {
        raw[pos++] = 0
        for (x in 0 until width) {
            val argb = pixels[x, y].toArgb()
            raw[pos++] = ((argb ushr 16) and 0xFF).toByte() // R
            raw[pos++] = ((argb ushr 8) and 0xFF).toByte() // G
            raw[pos++] = (argb and 0xFF).toByte() // B
            raw[pos++] = ((argb ushr 24) and 0xFF).toByte() // A
        }
    }

    val out = ByteArrayOutputStream()
    out.write(PNG_SIGNATURE)
    out.writeChunk("IHDR", ihdr(width, height))
    out.writeChunk("IDAT", deflate(raw))
    out.writeChunk("IEND", ByteArray(0))
    return out.toByteArray()
}

private fun ihdr(width: Int, height: Int): ByteArray = ByteArrayOutputStream().apply {
    writeBigEndian(width)
    writeBigEndian(height)
    write(8) // bit depth
    write(6) // colour type: truecolour with alpha
    write(0) // compression: deflate
    write(0) // filter method: adaptive (we use "none" per scanline)
    write(0) // interlace: none
}.toByteArray()

private fun deflate(data: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_SPEED).apply {
        setInput(data)
        finish()
    }
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFLATE_BUFFER)
    while (!deflater.finished()) {
        out.write(buffer, 0, deflater.deflate(buffer))
    }
    deflater.end()
    return out.toByteArray()
}

private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
    writeBigEndian(data.size)
    val typeBytes = type.toByteArray(Charsets.US_ASCII)
    write(typeBytes)
    write(data)
    val crc = CRC32().apply {
        update(typeBytes)
        update(data)
    }
    writeBigEndian(crc.value.toInt())
}

private fun ByteArrayOutputStream.writeBigEndian(value: Int) {
    write((value ushr 24) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 8) and 0xFF)
    write(value and 0xFF)
}

private const val BYTES_PER_PIXEL = 4
private const val DEFLATE_BUFFER = 64 * 1024

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

private val FILENAME_UNSAFE = Regex("[^A-Za-z0-9._-]")

private val FRAMEWORK_PREFIXES = listOf(
    // Harness internals + the runner/DSL class (ChipboxUiTest + the ChipboxUiTestKt file class),
    // but NOT the test classes — those share the base `…uitest` package and ARE what we want to name.
    "net.sigmabeta.chipbox.uitest.harness",
    "net.sigmabeta.chipbox.uitest.ChipboxUiTest",
    "androidx.",
    "org.junit",
    "junit.",
    "org.gradle",
    "worker.org.gradle",
    "kotlin",
    "java.",
    "javax.",
    "jdk.",
    "sun.",
    "dev.zacsweers",
    // ART/Android framework frames — on-device the stack top is dalvik.system.VMStack, not
    // java.lang.Thread (which `java.` already covers on the JVM).
    "dalvik.",
    "android.",
    "libcore.",
    "com.android.",
)
