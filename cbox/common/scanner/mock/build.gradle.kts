plugins {
    alias(libs.plugins.sage.jvm)
}

dependencies {
    api(projects.cbox.common.scanner)
    implementation(projects.cbox.common.repository.mock)
}
