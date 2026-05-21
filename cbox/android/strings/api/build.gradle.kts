import net.sigmabeta.sage.plugins.components.chipboxNamespace

plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = chipboxNamespace()
}

dependencies {
    api(projects.cbox.common.strings.api)

    implementation(projects.cbox.android.colors.api)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
