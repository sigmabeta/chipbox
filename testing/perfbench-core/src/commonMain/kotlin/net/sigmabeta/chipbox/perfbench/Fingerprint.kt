package net.sigmabeta.chipbox.perfbench

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The machine facts recorded alongside a result so two results are known to be comparable.
 *
 * Class-aware: a [HostFingerprint] and a [DeviceFingerprint] describe different machine classes and
 * are never compared with each other (see the comparability guards). Sealed so the serialized JSON
 * carries an explicit `class` discriminator (e.g. `"class": "host"`).
 */
@Serializable
sealed interface Fingerprint

/** A desktop/JVM host: CPU, OS/kernel, JVM, and the two source commits the run was built from. */
@Serializable
@SerialName("host")
data class HostFingerprint(
    @SerialName("cpu_model") val cpuModel: String,
    @SerialName("cpu_cores") val cpuCores: Int,
    val os: String,
    val kernel: String,
    val jvm: String,
    @SerialName("chipbox_commit") val chipboxCommit: String,
    @SerialName("sage_commit") val sageCommit: String,
) : Fingerprint

/** A physical Android device: hardware identity, Android build, ABI, and thermal state at start. */
@Serializable
@SerialName("device")
data class DeviceFingerprint(
    val manufacturer: String,
    val model: String,
    @SerialName("android_build") val androidBuild: String,
    val abi: String,
    @SerialName("thermal_status") val thermalStatus: String,
    @SerialName("chipbox_commit") val chipboxCommit: String,
    @SerialName("sage_commit") val sageCommit: String,
) : Fingerprint

/** The raw host facts, decoupled from how they were probed so [hostFingerprint] stays pure. */
data class HostEnvironment(
    val cpuModel: String,
    val cpuCores: Int,
    val os: String,
    val kernel: String,
    val jvm: String,
)

/** Builds a [HostFingerprint] from probed facts plus the two commits resolved by the caller. */
fun hostFingerprint(
    environment: HostEnvironment,
    chipboxCommit: String,
    sageCommit: String,
): HostFingerprint = HostFingerprint(
    cpuModel = environment.cpuModel,
    cpuCores = environment.cpuCores,
    os = environment.os,
    kernel = environment.kernel,
    jvm = environment.jvm,
    chipboxCommit = chipboxCommit,
    sageCommit = sageCommit,
)

/** The raw device facts, decoupled from how they were probed so [deviceFingerprint] stays pure. */
data class DeviceEnvironment(
    val manufacturer: String,
    val model: String,
    val androidBuild: String,
    val abi: String,
    val thermalStatus: String,
)

/** Builds a [DeviceFingerprint] from probed facts plus the two commits resolved by the caller. */
fun deviceFingerprint(
    environment: DeviceEnvironment,
    chipboxCommit: String,
    sageCommit: String,
): DeviceFingerprint = DeviceFingerprint(
    manufacturer = environment.manufacturer,
    model = environment.model,
    androidBuild = environment.androidBuild,
    abi = environment.abi,
    thermalStatus = environment.thermalStatus,
    chipboxCommit = chipboxCommit,
    sageCommit = sageCommit,
)
