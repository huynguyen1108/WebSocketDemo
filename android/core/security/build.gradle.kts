plugins {
    alias(libs.plugins.wschat.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.core.security"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
}
