# Release checklist

Use this checklist before publishing a public APK.

## Build quality

- Build from a clean release source tree.
- Confirm `debuggable=false` in the packaged manifest.
- Sign with the private release keystore.
- Never commit keystore files or passwords.
- Name the APK like `WUWA-VN-v2.2.0-release.apk`.
- Do not upload `app-debug.apk` as a public release file.
- Upload `sha256.txt` beside the APK.
- Upload the generated release `update.json`.

## Current release command

This repo currently uses a minimal native Android command-line build script instead of Gradle:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build-release.ps1
```

The script downloads and verifies the official Shizuku API/provider AARs before compiling.
Version name and version code come from `version.properties` unless release automation overrides `WUWA_VERSION_NAME` from the Git tag.

Verify the APK:

```bash
apksigner verify --print-certs WUWA-VN-v2.2.0-release.apk
sha256sum WUWA-VN-v2.2.0-release.apk
```

## Future Gradle release build type

When the app moves to a full Gradle Android project, use a release build type like:

```kotlin
android {
    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

## Manifest update

- Keep only the nested `app` and `patch` objects in `update.json`.
- Confirm APK URL points to `Acceleratorer/WUWA-VN-Android`.
- Confirm the APK SHA-256 is real.
- Confirm the PAK SHA-256 is real.
- Confirm the changelog is user-readable.
- Confirm `force_update` is only true for critical fixes.

## GitHub Actions secrets

Store these as repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
