# Build setup (Gradle/Kotlin)

## Suggested toolchain
- **AGP**: 8.5.x
- **Kotlin**: 2.0.x
- **compileSdk**: 34 (ok to use 35)
- **minSdk**: 28
- **targetSdk**: 34

## Module structure
- Single app module `:app` producing `fi.iki.pnr.kioskhelper`.

## `build.gradle.kts` (app) essentials
- Plugins: `com.android.application`, `org.jetbrains.kotlin.android`
- DefaultConfig: min/target, `applicationId`, versionName/Code
- Enable R8 (default). Add a minimal `proguard-rules.pro` with keeps for receivers/admin.
- Dependencies: keep to `androidx.core:core-ktx`, `androidx.appcompat:appcompat` (only if needed).

### Sample snippets
**android block**
```kotlin
android {
  namespace = "fi.iki.pnr.kioskhelper"
  compileSdk = 34

  defaultConfig {
    applicationId = "fi.iki.pnr.kioskhelper"
    minSdk = 28
    targetSdk = 34
    versionCode = 1
    versionName = "0.1"
  }

  buildTypes {
    release { isMinifyEnabled = true }
    debug { isMinifyEnabled = false }
  }
}
```

**dependencies**
```kotlin
dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0") // optional
}
```

## Commands
```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

## Local properties
Ensure `local.properties` points to a valid Android SDK. No other secrets/config needed.

## R8/Keep rules (minimal)
```
-keep class fi.iki.pnr.kioskhelper.AdminReceiver { *; }
-keep class fi.iki.pnr.kioskhelper.BootReceiver { *; }
-keep class fi.iki.pnr.kioskhelper.HomeActivity { *; }
-keep class fi.iki.pnr.kioskhelper.KioskCommandActivity { *; }
```

---
