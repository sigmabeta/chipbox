plugins {
    alias(libs.plugins.android.test)
    // The SAGE base plugins (sage.android/jvm/kmp) apply ktlint + detekt per module, but
    // `com.android.test` isn't one of them — so this module applies them itself, with the same
    // root config, or its Kotlin would never be linted (in CI or locally).
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    // Build identity (chipbox + sage commits) from `.git` files instead of a configuration-time git
    // process; both stamp "unknown" when the build context has no `.git` (a source tarball).
    id("chipbox.build.identity")
}

detekt {
    config.setFrom("${rootDir.absolutePath}/detekt-config.yml")
    baseline = file("detekt-baseline.xml")
}

ktlint {
    version.set(libs.versions.ktlintTool.get())
}

// The Chipbox + sage commits are stamped into the device results fingerprint at build time (the
// benchmark process can't read git). Read from `.git` files by the build identity plugin — no
// configuration-time `git` process, and "unknown" when there is no `.git` to read.

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
        testInstrumentationRunnerArguments["chipboxCommit"] = buildIdentity.shortCommit(rootDir)
        testInstrumentationRunnerArguments["sageCommit"] = buildIdentity.shortCommit(File(rootDir, "sage"))
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
