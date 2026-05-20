plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

// ChipboxListViewModel (commonMain) is the base VM every Chipbox feature screen extends.
// ChipboxListEntry (androidMain) is the Compose scaffolding that binds a list VM to the
// Android sage list screens — it's pinned to the Android target until sage/android/ui/list
// itself moves to sage.kmp (Settings port slice 5).
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.ui.list"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.sage.common.list)
                api(libs.sage.common.appcomm)
                api(libs.sage.common.ui.strings)
                api(libs.sage.common.ui.components)
                api(libs.sage.common.logging)
                api(libs.androidx.lifecycle.viewmodel)
                api(projects.cbox.common.appcomm.api)
                api(projects.cbox.common.ui.vm.api)
            }
        }
        named("androidMain") {
            dependencies {
                implementation(libs.sage.android.ui.list)
                implementation(projects.cbox.android.ui.chrome.api)
                implementation(projects.cbox.android.ui.components.api)

                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.androidx.lifecycle.viewModelCompose)
            }
        }
    }
}
