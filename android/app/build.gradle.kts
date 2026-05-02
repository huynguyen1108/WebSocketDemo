plugins {
    alias(libs.plugins.wschat.android.application)
}

android {
    namespace = "com.example.wschat"
    defaultConfig {
        applicationId = "com.example.wschat"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(project(":feature:stock:presentation"))
    implementation(project(":feature:stock:data"))
    implementation(project(":feature:stock:domain"))
    implementation(project(":feature:chat:presentation"))
    implementation(project(":feature:chat:data"))
    implementation(project(":feature:chat:domain"))
    implementation(project(":feature:auth:presentation"))
    implementation(project(":feature:auth:data"))
    implementation(project(":feature:auth:domain"))
    implementation(project(":feature:order:presentation"))
    implementation(project(":feature:order:data"))
    implementation(project(":feature:order:domain"))
    implementation(project(":feature:video:presentation"))
    implementation(project(":feature:video:data"))
    implementation(project(":feature:video:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.timber)

    // XML Views (Material3 theme for StockXmlActivity)
    implementation(libs.material.views)
    implementation(libs.androidx.fragment.ktx)
}
