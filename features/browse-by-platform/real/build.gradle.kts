plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.features.browsebyplatform.real"
}

dependencies {
    api(projects.features.browseByPlatform.api)

    implementation(projects.cbox.android.ui.list.api)
    implementation(projects.cbox.common.appcomm.api)
    implementation(projects.cbox.common.strings.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.models.api)

    implementation(projects.features.gamesForPlatform.api)

    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
}
