plugins {
    alias(libs.plugins.sage.feature.real)
}

// Source-set placement:
//   - PlaybackStatusViewModel/State/Action.kt → src/main/java (= jvmSharedMain). State reaches
//     the `jvmSharedMain`-resident PlaybackDebugInfo / VolumeProcessor types (and uses
//     java.net.URLDecoder + String.format), so commonMain isn't an option without hoisting
//     foundational modules — same call SettingsViewModel makes.
//   - PlaybackStatusRoute.kt → expect (commonMain) + identical actuals (androidMain/jvmMain),
//     so the shared screenFor() in cbox/common/appui/api can reach it from commonMain while the
//     actuals see the jvmSharedMain VM. See the KDoc on the expect declaration.
kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.playbackStatus.api)

                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.ui.list.api)
            }
        }
        named("jvmSharedMain") {
            dependencies {
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.debugInfo.api)

                implementation(libs.sage.common.ui.components)
            }
        }
    }
}
