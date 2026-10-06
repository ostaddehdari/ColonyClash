import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ir.srun.colonyclash"
    compileSdk = 36

    defaultConfig {
        applicationId = "ir.srun.colonyclash"
        minSdk = 26
        targetSdk = 36
        versionCode = 81
        versionName = "0.8.1"
        val apiBaseUrl = providers.gradleProperty("API_BASE_URL").orElse("https://api.example.com").get()
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.replace("\"", "\\\"")}\"")
    }

    flavorDimensions += "market"
    productFlavors {
        create("play") {
            dimension = "market"
            buildConfigField("String", "MARKET", "\"play_global\"")
            buildConfigField("String", "PAYMENT_PROVIDER", "\"google_play\"")
        }
        create("iran") {
            dimension = "market"
            applicationIdSuffix = ".iran"
            versionNameSuffix = "-iran"
            buildConfigField("String", "MARKET", "\"direct_iran\"")
            buildConfigField("String", "PAYMENT_PROVIDER", "\"iran_gateway\"")
        }
        create("china") {
            dimension = "market"
            applicationIdSuffix = ".china"
            versionNameSuffix = "-china"
            buildConfigField("String", "MARKET", "\"direct_china\"")
            buildConfigField("String", "PAYMENT_PROVIDER", "\"china_store\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}


kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("17")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.google.zxing:core:3.5.3")
    add("playImplementation", "com.android.billingclient:billing-ktx:9.1.0")
}
