# AndroidManifest spec (canonical skeleton)

```xml
<manifest package="fi.iki.pnr.kioskhelper"
    xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Min set of permissions -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />

    <application
        android:label="Kiosk Helper"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:supportsRtl="true">

        <!-- Signature permission required to control kiosk -->
        <permission
            android:name="fi.iki.pnr.kioskhelper.permission.KIOSK_CONTROL"
            android:protectionLevel="signature"
            android:label="Control kiosk policy"
            android:description="Allows controlling Device Owner kiosk policy" />

        <!-- Device Admin receiver -->
        <receiver
            android:name=".AdminReceiver"
            android:exported="true"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_admin" />
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
            </intent-filter>
        </receiver>

        <!-- Minimal HOME activity -->
        <activity
            android:name=".HomeActivity"
            android:exported="true"
            android:excludeFromRecents="true"
            android:launchMode="singleInstance"
            android:taskAffinity=""
            android:theme="@style/Theme.Transparent">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- Headless command activity for ENABLE/DISABLE (requires signature permission) -->
        <activity
            android:name=".KioskCommandActivity"
            android:exported="true"
            android:permission="fi.iki.pnr.kioskhelper.permission.KIOSK_CONTROL"
            android:theme="@style/Theme.Transparent"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="fi.iki.pnr.kioskhelper.ACTION_ENABLE_KIOSK" />
                <action android:name="fi.iki.pnr.kioskhelper.ACTION_DISABLE_KIOSK" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- Boot receiver (fail-fast) -->
        <receiver
            android:name=".BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

**Notes:**
- Provide `@xml/device_admin` with minimal `<device-admin>`.
- Provide `@style/Theme.Transparent` (no UI).
- DO provisioning occurs outside this APK (QR/ADB). Manifest must be compatible.

---

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
