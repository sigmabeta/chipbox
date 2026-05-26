plugins {
    id("chipbox.feature.real")
    id("chipbox.kmp.test")
}

// ManageLibraryState/Action + the ViewModel are commonMain (contentsource/scanner/rescanStatus
// are all KMP now). ManageLibraryRoute is a commonMain `expect` with platform actuals for the
// folder picker behind ChipboxEvent.PickFolder: androidMain (SAF OpenDocumentTree), jvmMain
// (Swing JFileChooser), and an enforcement-only jsMain stub that just forwards events.
kotlin {
    js { nodejs() }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.manageLibrary.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)

                implementation(projects.cbox.common.contentsource.api)
                // Adding a folder starts a scan and opens the rescan-status screen.
                implementation(projects.cbox.common.scanner.api)
                implementation(projects.features.rescanStatus.api)
            }
        }
        named("androidMain") {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.contentsource.fake)
                implementation(projects.cbox.common.scanner.fake)
            }
        }
    }
}
