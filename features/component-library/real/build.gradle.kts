plugins {
    alias(chipbox.plugins.feature.real)
}

// The menu + mode ViewModels and their states are commonMain (pure UI-model assembly). The only
// platform split is the sample-text generator: SAGE's StringGenerator is a JVM-only type, so its
// `actual` lives in src/main/java (= jvmSharedMain, shared by the Android + JVM leaf targets) and
// the enforcement-only JS target (no debug UI) gets a deterministic stub in jsMain.
kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.componentLibrary.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                // SourceInfo for the image-bearing sample components.
                implementation(libs.sage.common.images)
                // The ListModel catalogue the gallery showcases.
                implementation(libs.sage.common.ui.components)
                implementation(libs.kotlinx.collections.immutable)
            }
        }
        named("jvmSharedMain") {
            dependencies {
                // StringGenerator (JVM-only) backs the sample-content `actual` in src/main/java.
                implementation(libs.sage.common.ui.strings)
            }
        }
    }
}
