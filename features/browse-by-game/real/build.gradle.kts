plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.features.browsebygame.real"
}

dependencies {
    api(projects.features.browseByGame.api)

    implementation(projects.cbox.android.ui.list.api)
    implementation(projects.cbox.common.appcomm.api)
    implementation(projects.cbox.common.strings.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.models.api)

    implementation(projects.features.gameDetail.api)

    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
