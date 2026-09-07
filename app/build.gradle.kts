import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.xiaoiubao.suixinji"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xiaoiubao.suixinji"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.4.0"
    }

    val releaseStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val releaseStorePassword = System.getenv("ANDROID_STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

    val releaseSigning = if (
        !releaseStorePath.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()
    ) {
        signingConfigs.create("release") {
            storeFile = file(releaseStorePath)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    } else null

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = releaseSigning
        }
    }

    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")
    implementation("org.jsoup:jsoup:1.23.2")
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
