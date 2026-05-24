plugins {
    alias(libs.plugins.sage.feature.real)
}

// RescanStatusState / the route `expect` are commonMain (pure renderer + entry point). The
// RescanStatusViewModel and the route `actual` live in src/main/java (= jvmSharedMain) because
// they reference Scanner from scanner.api, whose types are jvmSharedMain-only. One actual there
// serves both the Android and JVM leaf targets — there's no platform-specific UI here (unlike the
// folder picker), so no per-platform split is needed.
kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.rescanStatus.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                // SourceInfo for the game cover art on each event row.
                implementation(libs.sage.common.images)
            }
        }
        named("jvmSharedMain") {
            dependencies {
                implementation(projects.cbox.common.scanner.api)
                // GameDetail route key — tapping an added/updated game opens its detail screen.
                implementation(projects.features.gameDetail.api)
            }
        }
    }
}
