import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing is read from local.properties (git-ignored) or environment
// variables, so no keystore path or password is committed to the repository.
val keystoreProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) FileInputStream(propsFile).use { load(it) }
}
fun signingProp(key: String): String? =
    System.getenv(key) ?: keystoreProps.getProperty(key)

android {
    namespace = "com.phigros.offline"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phigros.offline"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    val releaseStoreFile = signingProp("keystore.file")
    val releaseStorePass = signingProp("keystore.password")
    val releaseAlias = signingProp("keystore.alias")
    val releaseKeyPass = signingProp("keystore.keyPassword")

    signingConfigs {
        if (releaseStoreFile != null && releaseStorePass != null && releaseAlias != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePass
                keyAlias = releaseAlias
                keyPassword = releaseKeyPass ?: releaseStorePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("dev.rikka.shizuku:api:12.2.0")
    implementation("dev.rikka.shizuku:provider:12.2.0")
}
