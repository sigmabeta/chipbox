package net.sigmabeta.chipbox.uitest.harness

import java.io.File

/**
 * Desktop (`jvmTest`) twin of the Android [platformArtifactDir]. The JVM run has no device-output
 * channel and a real `build/` dir, so it returns null — `artifactDir()` then uses its default: the
 * `chipbox.uitest.artifactDir` system property (the `jvmTest` task points it at
 * `build/uitest-failures`) or `<java.io.tmpdir>/chipbox-uitest`.
 *
 * Lives in `src/jvmTestPlatform` (added only to `jvmTest`, NOT mirrored to `androidDeviceTest`), so
 * each compilation sees exactly one of these — this one on desktop, the Android one on-device.
 */
internal fun platformArtifactDir(): File? = null

/** Desktop twin of the Android [platformTestArgument]: the `jvmTest` task passes the `-P` flag in as
 *  a system property. */
internal fun platformTestArgument(key: String): String? = System.getProperty(key)
