plugins {
    alias(libs.plugins.android.test)
}

// The Chipbox + sage commits are stamped into the device results fingerprint at build time (the
// benchmark process can't read git). Read them the same way :apps:android reads the build branch.
fun gitShortCommit(dir: File): String = providers.exec {
    commandLine("git", "-C", dir.absolutePath, "rev-parse", "--short", "HEAD")
}.standardOutput.asText.get().trim().ifEmpty { "unknown" }

android {
    namespace = "net.sigmabeta.chipbox.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Numbers are not representative on an emulator — flip this off when
        // benchmarking on physical hardware.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
        testInstrumentationRunnerArguments["chipboxCommit"] = gitShortCommit(rootDir)
        testInstrumentationRunnerArguments["sageCommit"] = gitShortCommit(File(rootDir, "sage"))
    }

    targetProjectPath = ":apps:android"
    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        // Match the :app benchmark variant by name.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    packaging {
        jniLibs {
            // The audio axis links the emulator `:native` cores, whose `.so`s only exist for the
            // ABIs we build (arm64-v8a on device, x86_64 on emulator). A transitive lib ships stray
            // armeabi-v7a/armeabi/x86 ABIs; drop them so the install resolves an ABI that has our
            // cores rather than a core-less primary one. Extract the `.so`s at install time
            // (extractNativeLibs=true) rather than mmap'ing from the APK, matching :apps:abrender-core.
            excludes += setOf("**/armeabi-v7a/**", "**/armeabi/**", "**/x86/**")
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
    // The perfbench contract the device results.json is written with (schema, fingerprint, math).
    implementation(projects.apps.perfbenchCore)
    // The reused raw-emulator-core render path (EmulatorBank/runRender) the device audio axis
    // times — the same KMP consumer that pulls each emulator's arm64 `.so` into this test APK.
    implementation(projects.apps.abrenderCore)
}
