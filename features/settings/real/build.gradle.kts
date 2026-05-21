plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
    alias(libs.plugins.metro)
}

// First feature `:real` module on sage.kmp. Hilt's KMP-blocking Gradle plugin was dropped at
// chipbox M6 (see docs/metro-migration.md); Metro's compiler plugin layers on top of
// `kotlin("multiplatform")` cleanly, so SettingsViewModel can now contribute into the same
// AppScope graph on both the Android app and the headless/desktop JVM target.
//
// Source-set placement:
//   - SettingsViewModel.kt → src/main/java (= jvmSharedMain). Uses `java.time.*` for the build-
//     date formatter; commonMain would require kotlinx-datetime — out of scope here.
//   - SettingsRoute.kt → src/androidMain/kotlin. Owns the SAF folder picker
//     (`rememberLauncherForActivityResult` + `ActivityResultContracts.OpenDocumentTree`) and
//     consumes ChipboxListEntry which is still androidMain (M9 slice 6 of docs/kmp-migration.md
//     promotes it). Desktop gets its own route in apps/jvm.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.features.settings.real"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.features.settings.api)

                implementation(projects.cbox.android.ui.list.api)
                implementation(libs.sage.common.di)
                implementation(projects.cbox.common.ui.fonts.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.settings.api)
                implementation(projects.cbox.common.debug.api)
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.scanner.api)
                implementation(projects.cbox.common.contentsource.api)

                implementation(libs.sage.common.appinfo)
                implementation(libs.sage.common.ui.components)
                implementation(libs.kotlinx.collections.immutable)

                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)
            }
        }
        named("androidMain") {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}
