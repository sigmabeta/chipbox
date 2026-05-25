@file:OptIn(
    dev.zacsweers.metro.gradle.DelicateMetroGradleApi::class,
    dev.zacsweers.metro.gradle.ExperimentalMetroGradleApi::class,
    dev.zacsweers.metro.gradle.RequiresIdeSupport::class,
)

import net.sigmabeta.sage.plugins.components.chipboxNamespace
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
    alias(libs.plugins.metro)
}

// Metro's contribution-hint codegen emits top-level declarations that Kotlin/JS incremental
// compilation rejects (KT-82395). Generate hints only for the JVM/Android targets and omit JS;
// this module's js() target is an enforcement-only purity gate (no runtime JS consumer), and it
// only uses constructor @Inject (no @ContributesTo), so JVM/Android DI is unaffected.
metro {
    enableTopLevelFunctionInjection = false
    generateContributionHintsInFir = false
    supportedHintContributionPlatforms = setOf(KotlinPlatformType.jvm, KotlinPlatformType.androidJvm)
}

// Sage.kmp so PlayerStatus + PlayerStatusViewModel are reachable from the shared
// `ChipboxAppUi` composable in `cbox/android/appui/api` (also KMP). No `android.*` imports
// in the sources today — pure Compose + androidx.lifecycle ViewModel. Same Metro
// multibinding contribution pattern as `features/settings/real` and `features/library/real`.
// The earlier `cbox.android.ui.theme.api` dep was unused (no AppTheme/ChipboxTheme refs in
// any source file); dropped during the KMP conversion.
kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = chipboxNamespace()
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(projects.cbox.common.ui.components.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.player.director.api)

                implementation(libs.sage.common.di)
                implementation(libs.sage.common.images)

                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)
                implementation(libs.jetbrains.compose.material.icons.extended)
            }
        }
    }
}
