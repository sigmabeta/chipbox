plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

fun gitBranch(): String = providers.exec {
    commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
}.standardOutput.asText.get().trim().ifEmpty { "unknown" }

android {
    namespace = "net.sigmabeta.chipbox"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.sigmabeta.chipbox"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("long", "BUILD_TIME_MS", "${System.currentTimeMillis()}L")
        buildConfigField("String", "BUILD_BRANCH", "\"${gitBranch()}\"")
    }

    signingConfigs {
        create("release") {
            // Set these in CircleCI project settings → Environment Variables.
            // chipbox.jks lives at the repo root and is committed to the repo.
            val ksAlias = System.getenv("CHIPBOX_KEY_ALIAS")
            val ksPass = System.getenv("CHIPBOX_KEYSTORE_PASSWORD")
            val keyPass = System.getenv("CHIPBOX_KEY_PASSWORD")
            if (ksAlias != null && ksPass != null && keyPass != null) {
                storeFile = rootProject.file("chipbox.jks")
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
            // Release-signed with chipbox.jks when the CHIPBOX_* env vars are
            // present (CI); falls back to debug signing for local builds.
            signingConfig = if (System.getenv("CHIPBOX_KEY_ALIAS") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            isProfileable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.cbox.android.appui.api)
    implementation(projects.cbox.android.strings.api)
    implementation(projects.cbox.android.artworkprovider.api)
    implementation(projects.cbox.android.database.all)
    implementation(projects.cbox.android.database.di)
    implementation(projects.cbox.android.services.api)
    implementation(projects.cbox.android.contentsource.file.di)
    implementation(projects.cbox.common.player.buffer.di)
    implementation(projects.cbox.common.player.director.di)
    implementation(projects.cbox.common.player.emulators.di)
    implementation(projects.cbox.android.player.emulators.di)
    implementation(projects.cbox.android.player.emulators.gba.di)
    implementation(projects.cbox.android.player.emulators.gme.di)
    implementation(projects.cbox.android.player.emulators.psf.di)
    implementation(projects.cbox.android.player.emulators.ssf.di)
    implementation(projects.cbox.android.player.emulators.twosf.di)
    implementation(projects.cbox.android.player.emulators.usf.di)
    implementation(projects.cbox.android.player.emulators.vgm.di)
    implementation(projects.cbox.android.player.generator.di)
    implementation(projects.cbox.android.player.speaker.di)
    implementation(projects.cbox.android.repository.di)
    implementation(projects.cbox.common.repository.di)
    implementation(projects.cbox.android.scanner.di)
    implementation(projects.cbox.android.ui.components.api)
    implementation(projects.cbox.android.coroutines.api)
    implementation(projects.cbox.android.storage.api)
    implementation(projects.cbox.common.debug.di)
    implementation(projects.cbox.common.debugInfo.di)
    implementation(projects.cbox.common.settings.di)

    implementation(libs.sage.common.appinfo)

    implementation(libs.sage.common.list)
    implementation(libs.sage.common.appcomm)
    implementation(libs.sage.common.analytics)
    implementation(libs.sage.common.logging)
    implementation(libs.sage.common.coroutines)
    implementation(libs.sage.common.ui.components)
    implementation(libs.sage.common.ui.strings)

    implementation(libs.sage.android.coroutines)
    implementation(libs.sage.android.logging)
    implementation(libs.sage.android.ui.list)
    implementation(libs.sage.android.ui.strings)
    implementation(libs.sage.android.ui.themes)

    implementation(libs.sage.fake.analytics)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.compose.runtime.tracing)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
}
