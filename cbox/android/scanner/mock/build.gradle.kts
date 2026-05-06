plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.scanner.mock"
}

dependencies {
    api(projects.cbox.common.scanner)
    implementation(projects.cbox.android.repository.mock)
}
