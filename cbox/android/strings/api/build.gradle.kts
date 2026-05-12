plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.strings"
}

dependencies {
    api(projects.cbox.common.strings.api)

    implementation(projects.cbox.android.colors.api)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
