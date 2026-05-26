plugins {
    id("chipbox.feature.real")
    id("chipbox.kmp.test")
}

// ViewModel/State/Action + the (previously expect/actual) PlaybackStatusRoute are all commonMain
// now — debugInfo/player-common are KMP, %.3f became formatDecimal, and java.net.URLDecoder became
// the urlDecodeUtf8 expect/actual (jvm URLDecoder + jsMain best-effort percent-decode).
kotlin {
    js { nodejs() }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.playbackStatus.api)

                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.ui.list.api)

                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.debugInfo.api)
                implementation(projects.cbox.common.utils.api)

                implementation(libs.sage.common.ui.components)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.debugInfo.fake)
            }
        }
    }
}
