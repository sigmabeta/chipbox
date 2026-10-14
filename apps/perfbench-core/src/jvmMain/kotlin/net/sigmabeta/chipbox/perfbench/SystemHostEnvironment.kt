package net.sigmabeta.chipbox.perfbench

import java.io.File

/**
 * Probes the running host for the facts a [HostFingerprint] records. JVM-only (it reads
 * `/proc` and `/etc/os-release`), so it lives in `jvmMain`; the fingerprint itself is built by the
 * pure [hostFingerprint] from the resulting [HostEnvironment].
 *
 * Linux is the primary desktop target, so `/proc/cpuinfo` and `/etc/os-release` are the preferred
 * sources; each falls back to a `System`/`java.*` property so a non-Linux host still yields a
 * fingerprint instead of throwing.
 */
object SystemHostEnvironment {
    fun detect(): HostEnvironment = HostEnvironment(
        cpuModel = cpuModel(),
        cpuCores = Runtime.getRuntime().availableProcessors(),
        os = osName(),
        kernel = System.getProperty("os.version") ?: "unknown",
        jvm = "${System.getProperty("java.vm.name")} ${System.getProperty("java.runtime.version")}",
    )

    private fun cpuModel(): String =
        File("/proc/cpuinfo").takeIf { it.isFile }?.readText()?.let(::parseCpuModel)
            ?: System.getProperty("os.arch")
            ?: "unknown"

    private fun osName(): String {
        val osRelease = File("/etc/os-release").takeIf { it.isFile }?.readLines().orEmpty()
        val pretty = osRelease.firstOrNull { it.startsWith("PRETTY_NAME=") }
            ?.substringAfter("PRETTY_NAME=")
            ?.trim()
            ?.trim('"')
        return pretty?.takeIf { it.isNotEmpty() } ?: System.getProperty("os.name") ?: "unknown"
    }
}

/**
 * Extracts the CPU model from `/proc/cpuinfo` — the first `model name` value. On some architectures
 * (e.g. arm64) there is no `model name` line, in which case the caller falls back to `os.arch`.
 */
internal fun parseCpuModel(cpuinfo: String): String? = cpuinfo.lineSequence()
    .firstOrNull { it.startsWith("model name") }
    ?.substringAfter(":")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
