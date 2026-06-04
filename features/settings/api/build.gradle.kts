plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.kotlin.serialization)
}

// Settings feature surface — the route key, the action hierarchy, and the SettingsState
// renderer that turns immutable state into ListModel rows. All commonMain so the desktop UI
// can build the same screen from the same types the Android UI does. SettingsViewModel
// itself stays in features/settings/real (Hilt-annotated; not yet wired on JVM).
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.features.settings.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.kotlinx.serialization.core)
                api(libs.kotlinx.collections.immutable)
                api(libs.sage.common.appcomm)
                api(libs.sage.common.list)
                api(libs.sage.common.appinfo)
                api(libs.sage.common.ui.components)
                api(libs.sage.common.ui.strings)
                api(projects.cbox.common.appcomm.api)
                api(projects.cbox.common.settings.api)
                api(projects.cbox.common.strings.api)
                api(projects.cbox.common.ui.fonts.api)
            }
        }
    }
}
