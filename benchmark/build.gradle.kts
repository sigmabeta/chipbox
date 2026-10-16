plugins {
    alias(libs.plugins.android.test)
    // The SAGE base plugins (sage.android/jvm/kmp) apply ktlint + detekt per module, but
    // `com.android.test` isn't one of them — so this module applies them itself, with the same
    // root config, or its Kotlin would never be linted (in CI or locally).
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

detekt {
    config.setFrom("${rootDir.absolutePath}/detekt-config.yml")
    baseline = file("detekt-baseline.xml")
}

ktlint {
    version.set(libs.versions.ktlintTool.get())
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
            // (extractNativeLibs=true) rather than mmap'ing from the APK, matching :testing:abrender-core.
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
    // The SAGE logging facade the device axes log begin/end progress through. This module resolves
    // :android:logging to its release variant, so the axes construct AndroidHatchet(enabled = true)
    // rather than relying on its BuildConfig.DEBUG default.
    implementation(libs.sage.android.logging)
    // The perfbench contract the device results.json is written with (schema, fingerprint, math).
    implementation(projects.testing.perfbenchCore)
    // The app's artist-name delimiter rule (`ARTIST_NAME_DELIMITERS`), so the artist this benchmark
    // opens is derived exactly as the app derives it.
    implementation(projects.cbox.common.repository.api)
    // The reused raw-emulator-core render path (EmulatorBank/runRender) the device audio axis
    // times — the same KMP consumer that pulls each emulator's arm64 `.so` into this test APK.
    implementation(projects.testing.abrenderCore)
}
