plugins {
    alias(libs.plugins.wschat.android.library)
}

android {
    namespace = "com.example.feature.chat.domain"
}

dependencies {
    implementation(project(":core:common"))
}
