plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.ronova.gryphonis"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.ronova.gryphonis"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    val keyStorePath = System.getenv("KEYSTORE_PATH")
    val keyStorePassword = System.getenv("KEYSTORE_PASSWORD")
    val keyAliasValue = System.getenv("KEY_ALIAS")
    val keyPasswordValue = System.getenv("KEY_PASSWORD")

    signingConfigs {
        if (!keyStorePath.isNullOrBlank() && !keyStorePassword.isNullOrBlank() &&
            !keyAliasValue.isNullOrBlank() && !keyPasswordValue.isNullOrBlank()) {
            create("release") {
                storeFile = file(keyStorePath)
                storePassword = keyStorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
