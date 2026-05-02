plugins {
    alias(libs.plugins.wschat.android.library)
}

android {
    namespace = "com.example.feature.video.domain"
}

dependencies {
    implementation(project(":core:common"))
}
