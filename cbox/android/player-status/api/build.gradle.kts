plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.playerstatus"
}

dependencies {
    implementation(projects.cbox.android.ui.components.api)
    implementation(projects.cbox.android.ui.theme.api)
    implementation(projects.cbox.common.models.api)
    implementation(projects.cbox.common.player.director.api)

    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
