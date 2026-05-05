plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.sigmabeta.chipbox"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.sigmabeta.chipbox"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.features.welcome)

    implementation(projects.core.activities)
    implementation(projects.core.database)
    implementation(projects.cbox.android.services)
    implementation(projects.core.player.buffer.di)
    implementation(projects.core.player.director.di)
    implementation(projects.core.player.emulators.di)
    implementation(projects.core.player.generator.di)
    implementation(projects.core.player.speaker.di)
    implementation(projects.core.player.status.di)
    implementation(projects.cbox.common.repository.database.di)
    implementation(projects.cbox.common.repository.memory.di)
    implementation(projects.cbox.common.repository.mock.di)
    implementation(projects.cbox.common.scanner.mock.di)
    implementation(projects.cbox.android.scanner.real.di)

    implementation(libs.sage.common.list)
    implementation(libs.sage.common.appcomm)
    implementation(libs.sage.common.analytics)
    implementation(libs.sage.common.logging)
    implementation(libs.sage.common.coroutines)
    implementation(libs.sage.common.ui.components)
    implementation(libs.sage.common.ui.strings)

    implementation(libs.sage.android.ui.list)
    implementation(libs.sage.android.ui.strings)
    implementation(libs.sage.android.ui.themes)
    implementation(libs.sage.android.logging)
    implementation(libs.sage.android.coroutines)
    implementation(libs.sage.fake.analytics)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.collections.immutable)
}
