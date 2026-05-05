plugins {
    id("sage.android")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.sigmabeta.chipbox.scanner.mock"
}

dependencies {
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    api(projects.cbox.common.scanner)
    implementation(projects.core.repository.mock)
}
