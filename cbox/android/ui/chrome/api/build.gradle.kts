plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
}

android {
    namespace = "net.sigmabeta.chipbox.ui.chrome"
}

dependencies {
    api(libs.sage.common.ui.components)
}
