# Safety spec

This app should behave like a safe patch manager, not a generic file copier.

## Allowed file targets

Only these relative Wuthering Waves paths should be modified:

```text
UE4Game/Client/Client/Saved/Config/Android/Engine.ini
UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini
UE4Game/Client/Client/Saved/Config/Android/MountLang_en.txt
UE4Game/Client/Client/Content/Paks/WuWaVH_99_P.pak
```

Reject paths with `..`, backtracking, or values outside this allowlist.

## Patch flow

```text
Detect game folder
Show dry run
Create backup
Download patch
Verify SHA-256
Apply Vietnamese patch
Verify target files exist
Show success
```

The app should never apply a patch until the backup step succeeds.

## Backup metadata

Each backup should include metadata similar to:

```json
{
  "created_at": "2026-05-15T22:30:00+07:00",
  "game_package": "com.kurogame.wutheringwaves.global",
  "app_version": "1.3.7",
  "patch_version": "2026.05.15",
  "files": [
    "Engine.ini",
    "DeviceProfiles.ini",
    "MountLang_en.txt",
    "WuWaVH_99_P.pak"
  ]
}
```

## Shizuku states

The app should handle all states without crashing:

```kotlin
enum class ShizukuState {
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_DENIED,
    READY
}
```

Show beginner-friendly instructions for each blocked state.

## Integrity verification

Every downloaded APK, manifest payload, config, or patch file should have a known SHA-256 before it is installed or applied.

```kotlin
fun sha256Of(file: File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun verifySha256(file: File, expected: String): Boolean {
    return sha256Of(file).equals(expected, ignoreCase = true)
}
```

If verification fails, delete the downloaded file and stop the operation.

## User-facing actions

Keep the main actions simple:

- Install Vietnamese Patch
- Update Vietnamese Patch
- Restore Original Files
- Check Game Folder
- Open Shizuku

## Logs

The app should offer:

- Copy Debug Log
- Export Log
- Send Issue Report

Logs should include app version, Android version, Shizuku state, permission state, game folder result, backup result, download result, SHA-256 result, and patch result.
