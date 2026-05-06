plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.scanner.real"
}

dependencies {
    api(projects.cbox.common.scanner)
    api(projects.cbox.common.repository)
    api(projects.cbox.android.contentsource.file)
    api(projects.cbox.common.readers)
    implementation(libs.sage.common.logging)
}
