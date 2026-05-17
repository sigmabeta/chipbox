plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.compose.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.features.search.real"
}

dependencies {
    api(projects.features.search.api)

    implementation(projects.cbox.android.ui.chrome.api)
    implementation(projects.cbox.android.ui.list.api)
    implementation(projects.cbox.android.ui.components.api)
    implementation(projects.cbox.android.strings.api)
    implementation(projects.cbox.common.appcomm.api)
    implementation(projects.cbox.common.strings.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.models.api)
    implementation(projects.features.gameDetail.api)
    implementation(projects.features.artistDetail.api)

    implementation(libs.sage.common.appcomm)
    implementation(libs.sage.common.images)
    implementation(libs.sage.common.ui.components)
    implementation(libs.sage.common.ui.strings)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
}
