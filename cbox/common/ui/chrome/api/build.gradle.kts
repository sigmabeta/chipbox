import net.sigmabeta.sage.plugins.components.chipboxNamespace

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = chipboxNamespace()
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.sage.common.ui.components)
                api(projects.cbox.common.appcomm.api)
            }
        }
    }
}
