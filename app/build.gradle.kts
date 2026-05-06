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

    implementation(projects.cbox.android.activities)
    implementation(projects.cbox.android.database)
    implementation(projects.cbox.android.database.di)
    implementation(projects.cbox.android.services)
    implementation(projects.cbox.common.player.buffer.di)
    implementation(projects.core.player.director.di)
    implementation(projects.cbox.android.player.emulators.di)
    implementation(projects.core.player.generator.di)
    implementation(projects.cbox.android.player.speaker.di)
    implementation(projects.cbox.common.player.status.di)
    implementation(projects.cbox.android.repository.database.di)
    implementation(projects.cbox.common.repository.memory.di)
    implementation(projects.cbox.android.repository.mock.di)
    implementation(projects.cbox.android.scanner.mock.di)
    implementation(projects.cbox.android.scanner.real.di)

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

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.collections.immutable)
}
