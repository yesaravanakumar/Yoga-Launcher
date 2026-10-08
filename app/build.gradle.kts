plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.yoga.launcher"
    compileSdk = 34
    defaultConfig { applicationId = "com.yoga.launcher"; minSdk = 26; targetSdk = 34; versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
