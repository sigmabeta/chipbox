plugins {
    alias(libs.plugins.sage.feature.real)
}

// Source-set placement:
//   - SettingsViewModel.kt → src/main/java (= jvmSharedMain). Uses `java.time.*` for the build-
//     date formatter; commonMain would require kotlinx-datetime — out of scope here.
//   - SettingsRoute.kt → src/androidMain/kotlin. Owns the SAF folder picker
//     (`rememberLauncherForActivityResult` + `ActivityResultContracts.OpenDocumentTree`) and
//     consumes ChipboxListEntry which is still androidMain (M9 slice 6 of docs/kmp-migration.md
//     promotes it). Desktop gets its own route in apps/jvm.
kotlin {
    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.features.settings.api)

                implementation(projects.cbox.common.ui.list.api)
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
            }
        }
        named("androidMain") {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}
