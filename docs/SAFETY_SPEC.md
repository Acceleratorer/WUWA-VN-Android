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

The Shizuku user service should validate read targets by exact canonical path under the shared external storage root. Do not accept suffix-only matches.

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

The app should never apply a patch until backup and restore behavior are tested. Restore writing must come before patch writing.

## Backup metadata

Current `v2.5.0` backups are stored in app-specific external storage:

```text
Android/data/com.acceleratorer.wuwavn/files/WUWA-VH-Backup/<timestamp>/metadata.json
```

Public `Download/WUWA-VH-Backup` can be considered later, but only with careful Android storage handling.

Each backup should include copied config files and metadata similar to:

```json
{
  "created_at": "2026-05-15T22:30:00+07:00",
  "game_package": "com.kurogame.wutheringwaves.global",
  "app_version": "2.5.0",
  "patch_version": "2026.05.15",
  "backup_type": "shizuku_read_only_config_backup",
  "game_write_enabled": false,
  "restore_write_enabled": false,
  "files": [
    {
      "display_name": "Engine.ini",
      "relative_path": "UE4Game/Client/Client/Saved/Config/Android/Engine.ini",
      "sha256": "...",
      "size_bytes": 1234
    }
  ],
  "missing_files": []
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
- Copy Backup Path
- Check Game Folder
- Open Shizuku

## Roadmap order

```text
previous milestone: restore dry-run only
v2.5.0: restore write unlock for verified original config backups
v2.6.0: patch write unlock
```

## Restore dry-run

Restore dry-run may read backup sessions from app-specific backup storage, parse `metadata.json`, verify the SHA-256 of each backed-up file, and show a restore plan.

Restore dry-run should flag:

- Missing `metadata.json`
- Missing backup files
- SHA-256 mismatch
- Unsafe metadata paths outside the backup allowlist
- Metadata display names that do not match the allowlisted relative path

## Restore write

`v2.5.0` may restore original config files, but only from trusted backup metadata and only after two confirmations.

Restore write must require:

- Shizuku state is `READY`
- Wuthering Waves Global is detected
- `backup_type` is `shizuku_read_only_config_backup`
- `game_package` is `com.kurogame.wutheringwaves.global`
- `restore_write_enabled` is explicitly `false`
- Every file in the restore plan is `VERIFIED`
- The restore plan contains exactly `Engine.ini`, `DeviceProfiles.ini`, and `MountLang_en.txt`

Restore write must not:

- Restore partial backups
- Restore missing or hash-mismatched files
- Restore PAK files
- Apply the Vietnamese patch
- Create arbitrary game paths

## Logs

The app should offer:

- Copy Debug Log
- Export Log
- Send Issue Report

Logs should include app version, Android version, Shizuku state, permission state, game folder result, backup result, download result, SHA-256 result, and patch result.
