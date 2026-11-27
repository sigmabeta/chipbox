import net.sigmabeta.sage.plugins.components.namespaceFromPath

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.sage.compose.kmp)
}

kotlin {
    android {
        namespace = namespaceFromPath()
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                // Re-exported so consumers get SAGE's ListModel vocabulary, the string/action seams,
                // and the shared renderer suite's entry points (ListModel.Content, SageRenderers,
                // CrossfadeText/CrossfadeImage, ...).
                api(libs.sage.common.ui.composables)

                // `Icon.vector()` (NowPlayingHomeCard's art placeholder + controls); the Icon type
                // itself arrives transitively through composables -> common.ui.components.
                implementation(libs.sage.common.ui.iconsReal)

                // ProvideChipboxText installs LocalChipboxStringProvider alongside the SAGE locals.
                implementation(projects.cbox.common.strings.api)
            }
        }
        named("androidMain") {
            dependencies {
                // BitmapGenerator (android.graphics gradient) for the inspection-mode previewImage
                // actual — reused so recorded Paparazzi goldens stay byte-identical to pre-6f.
                implementation(projects.cbox.android.images.api)
            }
        }
    }
}
