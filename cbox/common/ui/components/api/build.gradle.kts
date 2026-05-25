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

                implementation(libs.sage.common.appcomm)
                implementation(libs.sage.common.images)

                implementation(libs.sage.common.ui.perfCompose)
                implementation(libs.sage.common.ui.iconsReal)

                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.ui.fonts.api)

                implementation(libs.coil.kt.core)
                implementation(libs.coil.kt.compose)
            }
        }
        named("androidMain") {
            dependencies {
                implementation(projects.cbox.android.ui.theme.api)
                implementation(libs.androidx.compose.ui.tooling.preview)
                // ComposeViewAdapter (the renderer Android Studio's preview panel loads) lives in
                // ui-tooling, not ui-tooling-preview. Without it on the android classpath the
                // preview surface throws ClassNotFoundException. KMP androidLibrary has no
                // debug-only source set, so it rides on androidMain alongside the annotations.
                implementation(libs.androidx.compose.ui.tooling)
                // rememberChipboxStringProvider() loads the single multiplatform string source
                // (composeResources) through the @Composable stringResource API — the same machinery
                // Font(FontResource) already uses under Paparazzi — so the preview wrapper
                // (ChipboxPreview) installs a real StringProvider into LocalChipboxStringProvider,
                // letting M9-slice-6c composables render in @Preview / Paparazzi as at runtime.
                implementation(projects.cbox.common.strings.real)
                // BitmapGenerator (android.graphics gradient) for the inspection-mode FakeImage
                // actual — reused so recorded Paparazzi goldens stay byte-identical to pre-6f.
                implementation(projects.cbox.android.images.api)
            }
        }
    }
}
