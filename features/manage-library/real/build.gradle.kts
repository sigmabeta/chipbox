plugins {
    alias(libs.plugins.sage.feature.real)
}

// Source-set placement mirrors features/settings/real:
//   - ManageLibraryState / ManageLibraryAction → commonMain (pure renderer + action types).
//   - ManageLibraryViewModel → src/main/java (= jvmSharedMain). Depends on LibrarySource from
//     contentsource.api, which is a JVM-only module and so can't be a commonMain dependency.
//   - ManageLibraryRoute → commonMain `expect` with androidMain (SAF OpenDocumentTree) and
//     jvmMain (Swing JFileChooser) actuals, since the folder picker for ChipboxEvent.PickFolder
//     is platform-specific.
kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.manageLibrary.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
            }
        }
        named("jvmSharedMain") {
            dependencies {
                implementation(projects.cbox.common.contentsource.api)
            }
        }
        named("androidMain") {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}
