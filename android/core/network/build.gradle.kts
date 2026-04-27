plugins {
    alias(libs.plugins.wschat.android.library)
}

android {
    namespace = "com.example.core.network"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.core.ktx)
}
