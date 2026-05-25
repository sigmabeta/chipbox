import net.sigmabeta.sage.plugins.components.chipboxNamespace

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

// ChipboxListViewModel + ChipboxListEntry both live in commonMain — ChipboxListEntry is the
// Compose scaffolding that binds a list VM to sage's GridScreen / ListScreen (also commonMain).
kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = chipboxNamespace()
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

                implementation(libs.sage.common.ui.listScreens)
                implementation(projects.cbox.common.ui.chrome.api)
                implementation(projects.cbox.common.ui.components.api)
            }
        }
    }
}
