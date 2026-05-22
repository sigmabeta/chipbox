import net.sigmabeta.sage.plugins.components.chipboxNamespace

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

kotlin {
    androidLibrary {
        namespace = chipboxNamespace()
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.sage.common.ui.components)

                implementation(libs.sage.common.appcomm)
                implementation(libs.sage.common.images)

                implementation(libs.sage.android.perf)
                implementation(libs.sage.android.ui.icons)

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
                // AndroidStringProvider + the ChipboxStringId.id() → R.string mapping: the preview
                // wrapper (ChipboxPreview) installs an Android-resource-backed StringProvider into
                // LocalChipboxStringProvider so composables that read the local (M9 slice 6c) render
                // in @Preview / Paparazzi the same way MainActivity wires them at runtime. Both are
                // androidMain-only (preview surface), so the JVM variant is unaffected.
                implementation(libs.sage.android.ui.strings)
                implementation(projects.cbox.android.strings.api)
                // BitmapGenerator (android.graphics gradient) for the inspection-mode FakeImage
                // actual — reused so recorded Paparazzi goldens stay byte-identical to pre-6f.
                implementation(projects.cbox.android.images.api)
            }
        }
    }
}
