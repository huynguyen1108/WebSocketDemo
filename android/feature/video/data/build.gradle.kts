plugins {
    alias(libs.plugins.wschat.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.feature.video.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":feature:video:domain"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
