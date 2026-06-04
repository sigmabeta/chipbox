import net.sigmabeta.sage.plugins.components.namespaceFromPath

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.sage.compose.kmp)
}

kotlin {
    androidLibrary {
        namespace = namespaceFromPath()
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
