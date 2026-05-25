plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
}

android {
    namespace = "net.sigmabeta.chipbox.ui.previews"
}

dependencies {
    implementation(projects.cbox.android.ui.theme.api)
    implementation(projects.cbox.common.ui.components.api)
    implementation(projects.cbox.common.strings.real)
    implementation(projects.cbox.common.strings.api)
    implementation(projects.cbox.common.models.api)

    implementation(libs.sage.common.list)
    implementation(libs.sage.common.ui.listScreens)
    implementation(libs.sage.common.appcomm)
    implementation(libs.sage.common.ui.strings)
    implementation(libs.sage.common.ui.components)
    implementation(libs.sage.common.logging)
    implementation(libs.sage.common.ui.perfCompose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // PreviewTestUtils references Paparazzi's DeviceConfig. Only leaf :features:<x>:screenshot
    // modules consume this module and nothing depends on them, so the Paparazzi runtime has no
    // path into the shipped app — a plain implementation dependency is sufficient.
    implementation(libs.paparazzi.runtime)
}
