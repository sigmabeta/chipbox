plugins {
    alias(chipbox.plugins.feature.real)
    alias(chipbox.plugins.kmp.test)
}

// SettingsViewModel is commonMain now (its deps are all KMP); the build-date java.time formatting
// became the formatLongDate expect/actual (jvm java.time + jsMain stub). SettingsRoute stays a
// commonMain `expect` with platform actuals for the folder picker behind ChipboxEvent.PickFolder:
// androidMain (SAF OpenDocumentTree), jvmMain (push the in-app :features:folder-picker screen —
// replaces the old Swing JFileChooser), and an enforcement-only jsMain stub that just forwards
// events.
kotlin {
    sourceSets {
        named("commonMain") {
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
                // PlaybackStatus route key for SettingsAction.PlaybackStatusClicked -> NavigateTo.
                implementation(projects.features.playbackStatus.api)
                // ErrorLog route key for SettingsAction.ErrorLogClicked -> NavigateTo.
                implementation(projects.features.errorLog.api)
                // CrashLog route key for SettingsAction.CrashLogClicked -> NavigateTo.
                implementation(projects.features.crashLog.api)
                // ComponentLibrary route key for SettingsAction.ComponentLibraryClicked -> NavigateTo.
                implementation(projects.features.componentLibrary.api)
                // ManageLibrary route key for SettingsAction.ManageLibraryClicked -> NavigateTo.
                implementation(projects.features.manageLibrary.api)
                // RescanStatus route key — navigated to when a scan starts / from the in-progress row.
                implementation(projects.features.rescanStatus.api)

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
        named("jvmMain") {
            dependencies {
                // FolderPicker route key — the JVM actual routes PickFolder to it instead of
                // launching JFileChooser.
                implementation(projects.features.folderPicker.api)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.settings.fake)
                implementation(projects.cbox.common.debug.fake)
                implementation(projects.cbox.common.repository.fake)
                implementation(projects.cbox.common.scanner.fake)
                implementation(projects.cbox.common.contentsource.fake)
            }
        }
    }
}
