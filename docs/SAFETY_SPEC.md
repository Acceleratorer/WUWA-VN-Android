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

The app should only apply a patch after backup and restore behavior are tested. The first patch writer must stay PAK-only.

## Backup metadata

Current backups are stored in app-specific external storage:

```text
Android/data/com.acceleratorer.wuwavn/files/WUWA-VH-Backup/<timestamp>/metadata.json
```

Public `Download/WUWA-VH-Backup` can be considered later, but only with careful Android storage handling.

Each backup should include copied config files and metadata similar to:

```json
{
  "created_at": "2026-05-15T22:30:00+07:00",
  "game_package": "com.kurogame.wutheringwaves.global",
  "app_version": "3.3.6",
  "patch_version": "wuwa-3.3-vi-2026.05",
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
previous milestone: restore write unlock for verified original config backups
v2.6.0: PAK-only patch write unlock
v2.7.0: Safe / Default config preset write unlock
v3.3.0: WUWA Global 3.3 compatibility metadata and Remove Patch dry-run
v3.3.1: Remove Patch write unlock with MountLang rollback
v3.3.2: bundled launcher icon refresh
v3.3.3: smart installed-state detection and UI action gating
v3.3.4: Balanced config preset dry-run after smart state validation
v3.3.5: Balanced config preset write after dry-run validation
v3.3.6: Balanced write stability gate requiring PATCHED state
future milestone: Performance config preset write
future milestone: Max Graphics preset with strong warning
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

Restore write may restore original config files, but only from trusted backup metadata and only after two confirmations.

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

## Patch write

`v2.6.0` may write only `WuWaVH_99_P.pak` into the allowlisted WUWA Paks folder.

Patch write must require:

- Wuthering Waves Global is detected
- Shizuku state is `READY`
- A trusted VERIFIED backup exists
- The local PAK exists in app storage
- Local PAK SHA-256 matches the manifest
- Target path is exactly `UE4Game/Client/Client/Content/Paks/WuWaVH_99_P.pak`
- Two confirmations before writing

Patch write must:

- Stream the PAK in chunks instead of sending one large Binder payload
- Write only to a service-managed temporary file first
- Verify temp size and SHA-256 before replacing the target
- Re-read the target size and SHA-256 after write

Patch write must not:

- Modify `Engine.ini`, `DeviceProfiles.ini`, or `MountLang_en.txt` inside the PAK-only flow
- Write arbitrary PAK names
- Enable Balanced, Performance, or Max Graphics presets

## Safe config preset write

`v2.7.0` may write only the bundled Safe / Default templates for:

```text
UE4Game/Client/Client/Saved/Config/Android/Engine.ini
UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini
UE4Game/Client/Client/Saved/Config/Android/MountLang_en.txt
```

Safe config preset write must require:

- Wuthering Waves Global is detected
- Shizuku state is `READY`
- A trusted VERIFIED backup exists
- Restore write is available through the same exact allowlisted config service
- The preset is exactly Safe / Default
- The template set contains exactly the three config files
- Two confirmations before writing

Safe config preset write must:

- Use only bundled templates
- Avoid Balanced, Performance, Max Graphics, FPS, resolution, Vulkan, or quality CVars
- Verify each template SHA-256 before writing
- Re-read each target file and verify SHA-256 after writing

Safe config preset write must not:

- Write arbitrary config paths
- Write PAK files
- Write Balanced, Performance, or Max Graphics presets
- Continue if the trusted backup is missing or incomplete

## Game compatibility metadata

`update.json` uses manifest version 3 and should include game compatibility metadata:

```json
{
  "manifest_version": 3,
  "app": {
    "version_name": "3.3.6",
    "version_code": 40,
    "supported_game_version": "3.3",
    "minimum_game_version": "3.3"
  },
  "game": {
    "name": "Wuthering Waves",
    "server": "Global",
    "package": "com.kurogame.wutheringwaves.global",
    "version": "3.3"
  }
}
```

The app should show:

```text
WUWA Global detected
Game version: 3.3.x
Launcher compatibility: WUWA Global 3.3
Status: compatible
```

If Android does not expose a game version, show:

```text
Game package detected, version unknown.
```

Do not block users only because the game version is unknown.

## Remove patch write

`v3.3.1` may remove only `WuWaVH_99_P.pak` after restoring `MountLang_en.txt` from a trusted VERIFIED backup.

Remove patch write must require:

- Plan only `UE4Game/Client/Client/Content/Paks/WuWaVH_99_P.pak`
- Wuthering Waves Global is detected
- Shizuku state is `READY`
- A trusted VERIFIED backup exists
- `MountLang_en.txt` in that backup is `VERIFIED`
- Keep the target allowlisted
- Two confirmations before writing or deleting

Remove patch write must:

- Restore `MountLang_en.txt` from the trusted backup before deleting the PAK
- Delete only `WuWaVH_99_P.pak`
- Verify the PAK target no longer exists after delete
- Leave `Engine.ini` and `DeviceProfiles.ini` untouched

Remove patch write must not:

- Delete arbitrary PAK names
- Modify `Engine.ini`
- Modify `DeviceProfiles.ini`
- Modify `MountLang_en.txt` from any unverified source

## Balanced config preset write

`v3.3.6` may write only the bundled Balanced templates for:

```text
UE4Game/Client/Client/Saved/Config/Android/Engine.ini
UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini
UE4Game/Client/Client/Saved/Config/Android/MountLang_en.txt
```

Balanced config preset write must require:

- Wuthering Waves Global is detected
- Shizuku state is `READY`
- A trusted VERIFIED backup exists
- The preset availability is `WRITE_ENABLED`
- The current patch state is `PATCHED`
- The template set contains exactly the three config files
- Two confirmations before writing

Balanced config preset write must:

- Use only bundled templates
- Avoid FPS unlock, Vulkan override, resolution override, and high-risk graphics tokens
- Verify each template SHA-256 before writing
- Re-read each target file and verify SHA-256 after writing

Balanced config preset write must not:

- Add a new Shizuku write method
- Write PAK files
- Continue when the current patch state is `ORIGINAL`, `PARTIAL`, or `UNKNOWN`
- Create a state where `MountLang_en.txt` points to `WuWaVH_99_P.pak` while the PAK is missing
- Enable Performance or Max Graphics

## Installed state detection

`v3.3.3` may read allowlisted state only; it must not add new write operations.

Installed state detection may read:

- `UE4Game/Client/Client/Saved/Config/Android/Engine.ini`
- `UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini`
- `UE4Game/Client/Client/Saved/Config/Android/MountLang_en.txt`
- PAK existence for `UE4Game/Client/Client/Content/Paks/WuWaVH_99_P.pak`

Patch state rules:

- `PATCHED`: PAK exists and `MountLang_en.txt` points to `WuWaVH_99_P.pak`
- `ORIGINAL`: PAK is missing and `MountLang_en.txt` exists but does not point to `WuWaVH_99_P.pak`
- `PARTIAL`: PAK and `MountLang_en.txt` disagree, or `MountLang_en.txt` is missing
- `UNKNOWN`: game/Shizuku/state read is unavailable

Smart UI gating must not replace write preconditions. It only disables unsafe duplicate actions earlier in the UI.

## Logs

The app should offer:

- Copy Debug Log
- Export Log
- Send Issue Report

Logs should include app version, Android version, Shizuku state, permission state, game folder result, backup result, download result, SHA-256 result, and patch result.
