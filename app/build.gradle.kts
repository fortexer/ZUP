plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes ONLY from environment variables (GitHub Secrets in CI).
// Nothing secret is stored in the repository. Without them, release stays unsigned.
val ksPath: String? = System.getenv("KEYSTORE_PATH")
val ksPassword: String? = System.getenv("KEYSTORE_PASSWORD")
val ksAlias: String? = System.getenv("KEY_ALIAS")
val ksKeyPassword: String? = System.getenv("KEY_PASSWORD")
val hasSigning = !ksPath.isNullOrBlank() && !ksPassword.isNullOrBlank() &&
        !ksAlias.isNullOrBlank() && !ksKeyPassword.isNullOrBlank()

android {
    namespace = "com.fortexer.zup"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fortexer.zup"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = ksPassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            // R8/resource shrinking intentionally off: the app has no reflection or libraries,
            // and it could not be test-run here, so size savings are not worth any risk.
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
