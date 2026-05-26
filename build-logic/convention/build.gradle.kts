import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "net.sigmabeta.chipbox.buildlogic"

// Build-logic runs inside the Gradle daemon (JDK 21); targeting 21 lets the convention classpath
// consume Gradle plugins published for Java 21 (e.g. Paparazzi 2.0.0-alpha). Module targets are
// still JVM 17 (set by the SAGE base plugins these chipbox plugins layer on).
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

// These chipbox-specific convention plugins layer on the SAGE base plugins (sage.kmp, sage.di,
// sage.compose.*) by id; applying a plugin programmatically resolves it against the defining
// build's classpath, so depend on the SAGE convention (the `libs` helper rides along too). The
// third-party Gradle plugins whose types/ids the chipbox plugins reference are declared explicitly.
dependencies {
    implementation("net.sigmabeta.sage.buildlogic:convention")
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    // implementation, not compileOnly: applied by id at runtime — ChipboxFeatureApiPlugin applies
    // kotlin serialization; ChipboxScreenshotPlugin applies paparazzi — so their classes must be on
    // the runtime classpath, not just compile.
    implementation(libs.kotlin.serialization.gradlePlugin)
    implementation(libs.paparazzi)
    // ChipboxFeatureRealPlugin applies Metro and configures MetroPluginExtension (KT-82395 gate).
    implementation(libs.metro.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("chipboxFeatureApi") {
            id = "chipbox.feature.api"
            implementationClass = "ChipboxFeatureApiPlugin"
        }
        register("chipboxFeatureReal") {
            id = "chipbox.feature.real"
            implementationClass = "ChipboxFeatureRealPlugin"
        }
        register("chipboxEmulatorReal") {
            id = "chipbox.emulator.real"
            implementationClass = "ChipboxEmulatorRealPlugin"
        }
        register("chipboxEmulatorNative") {
            id = "chipbox.emulator.native"
            implementationClass = "ChipboxEmulatorNativePlugin"
        }
        register("chipboxScreenshot") {
            id = "chipbox.screenshot"
            implementationClass = "ChipboxScreenshotPlugin"
        }
        register("chipboxKmpTest") {
            id = "chipbox.kmp.test"
            implementationClass = "ChipboxKmpTestPlugin"
        }
    }
}
