plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

// Release signing: read from environment variables (set by the GitHub workflow from repository secrets).
// If they are missing, the release build is simply left unsigned, and debug builds are not affected.
val ksFile = System.getenv("YOGA_KEYSTORE_FILE")
val ksPass = System.getenv("YOGA_KEYSTORE_PASSWORD")
val ksAlias = System.getenv("YOGA_KEY_ALIAS")
val keyPass = System.getenv("YOGA_KEY_PASSWORD") ?: ksPass
val canSign = !ksFile.isNullOrBlank() && file(ksFile).exists() && !ksPass.isNullOrBlank() && !ksAlias.isNullOrBlank()

android {
    namespace = "com.yoga.launcher"
    compileSdk = 36
    defaultConfig { applicationId = "com.yoga.launcher"; minSdk = 26; targetSdk = 36; versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1; versionName = "1.64" }
    signingConfigs {
        if (canSign) create("release") {
            storeFile = file(ksFile!!)
            storePassword = ksPass
            keyAlias = ksAlias
            keyPassword = keyPass
        }
    }
    buildTypes {
        release {
            // No code shrinking: the page talks to Kotlin through @JavascriptInterface methods that R8 could rename.
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
