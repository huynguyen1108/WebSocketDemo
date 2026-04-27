plugins {
    alias(libs.plugins.wschat.android.library)
}

android {
    namespace = "com.example.feature.auth.domain"
}

dependencies {
    implementation(project(":core:common"))
}
