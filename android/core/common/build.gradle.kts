plugins {
    alias(libs.plugins.wschat.android.library)
}

android {
    namespace = "com.example.core.common"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
