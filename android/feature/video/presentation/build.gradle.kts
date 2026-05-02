plugins {
    alias(libs.plugins.wschat.android.feature)
}

android {
    namespace = "com.example.feature.video.presentation"
    packaging {
        jniLibs.pickFirsts += "**/libjingle_peerconnection_so.so"
    }
}

dependencies {
    implementation(project(":feature:video:domain"))
    implementation(project(":core:security"))
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.webrtc.android)
}
