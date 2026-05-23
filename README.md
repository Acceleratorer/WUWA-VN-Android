# WUWA Việt Hoá Android

Ứng dụng hỗ trợ cài bản Việt hoá cho Wuthering Waves trên Android.

> Trạng thái hiện tại: bản `v3.3.18` là WUWA Global 3.3 Compose diagnostics/snapshot polish release. App hiển thị Diagnostics Summary và State Snapshot Preview ngay trên màn Compose, trong khi vẫn giữ nguyên Shizuku/write logic. Max Graphics vẫn khóa.

## Tính Năng

- Chuẩn bị luồng cài bản Việt hoá cho Wuthering Waves bản Global
- Cập nhật bản dịch mới nhất từ GitHub Releases
- Sao lưu read-only các file cấu hình gốc trước khi chỉnh sửa
- Restore dry-run: liệt kê backup, verify metadata/SHA-256, show restore plan
- Restore file gốc từ backup đã VERIFIED bằng Shizuku
- Cài bản Việt hoá PAK-only: chỉ ghi `WuWaVH_99_P.pak`
- Hỗ trợ Shizuku để thao tác với thư mục game
- Hiển thị compatibility với Wuthering Waves Global `3.3`
- Smart State Detection: nhận diện Original / Patched / Partial / Unknown
- Hiển thị trạng thái PAK, `MountLang_en.txt`, Safe config, trusted backup và recommended action
- UI tự bật/tắt action theo trạng thái để tránh bấm đè hoặc thao tác sai
- Ghi config preset **Safe / Default** từ template bundled, không bật CVars đồ hoạ nặng
- Remove Vietnamese Patch: restore `MountLang_en.txt` từ backup VERIFIED, xoá đúng `WuWaVH_99_P.pak`, rồi verify PAK không còn tồn tại
- Balanced config preset write: xem dry-run, xác nhận hai lần, rồi ghi đúng 3 file config bằng template bundled
- Balanced chỉ được apply khi Vietnamese patch đã cài xong và state là PATCHED
- Balanced vẫn chặn FPS unlock, Vulkan override, resolution override, và high-risk graphics tokens
- Performance config preset write: xem dry-run, xác nhận hai lần, rồi ghi đúng 3 file config bằng template bundled
- Performance chỉ được apply khi Vietnamese patch đã cài xong và state là PATCHED
- Performance vẫn chặn FPS unlock, Vulkan override, resolution override, và high-risk graphics tokens
- Chuẩn bị cấu hình đồ hoạ theo lộ trình: Max Graphics vẫn khóa
- Header UI dùng artwork bundled trong APK, không tải ảnh từ mạng
- Icon app dùng artwork bundled trong APK
- Jetpack Compose home screen: màn chính runtime dùng Compose nhưng vẫn gọi controller/write flow cũ
- Compose diagnostics polish: hiển thị app/game/Shizuku/patch/backup/hint summary và preset policy/action state preview trên màn chính
- Install Help cho user không rành kỹ thuật: tải đúng APK release, Android 11+, BlueStacks Android 11 64-bit, unknown-source prompt, và Shizuku setup
- Tải PAK vào app storage và kiểm tra SHA-256 trước khi cho phép bước tiếp theo
- Ghi `metadata.json` với danh sách file backup thật, dung lượng và SHA-256
- Copy đường dẫn backup để dễ gửi log hoặc tự kiểm tra
- Copy debug log để gửi báo lỗi
- Copy State Snapshot để gửi nhanh trạng thái app/game/Shizuku/backup khi báo lỗi
- Recovery Guide hướng dẫn xử lý Original / Patched / Partial / Unknown state
- Gradle build path từ `v3.3.11`; manual PowerShell pipeline vẫn giữ làm legacy fallback
- GitHub Actions release automation từ `v3.3.12`: build/sign APK, tạo `sha256.txt`, và upload release assets
- First-run setup guide cho user mới
- Setup Checklist hiển thị game/Shizuku/backup/patch state và Ready YES/NO
- Shizuku Setup Help giải thích trạng thái Shizuku hiện tại
- Shortcut mở Developer Options, fallback sang Shizuku setup guide nếu ROM không hỗ trợ
- Màn chính được nhóm thành Setup, Patch, Config Presets, và Diagnostics

## Screenshots

| ORIGINAL | PATCHED | PARTIAL | UNKNOWN / Shizuku |
|---|---|---|---|
| Cần chụp state gốc: PAK missing, MountLang không trỏ PAK | Cần chụp state đã cài: PAK exists, MountLang trỏ PAK | Cần chụp state lệch: PAK/MountLang không khớp | Cần chụp khi Shizuku chưa READY hoặc không đọc được state |

## Apply State Matrix

| State | Install Vietnamese Patch | Apply Safe / Default | Apply Balanced | Apply Performance | Remove Patch | Restore Original |
|---|---|---|---|---|---|---|
| ORIGINAL | Enabled if trusted backup exists | Enabled if trusted backup exists | Blocked: install patch first | Disabled | Disabled | Enabled if trusted backup exists |
| PATCHED | Disabled | Enabled if trusted backup exists | Enabled if trusted backup exists | Enabled if trusted backup exists | Enabled if trusted backup exists | Enabled if trusted backup exists |
| PARTIAL | Disabled | Disabled | Disabled | Disabled | Enabled if trusted backup exists | Enabled if trusted backup exists |
| UNKNOWN | Disabled | Disabled | Disabled | Disabled | Disabled | Disabled |

## Diagnostics Snapshot

Nút **Copy State Snapshot** copy thông tin ngắn gọn để debug issue report:

```text
WUWA VN State Snapshot
App version: 3.3.18 (52)
Game package: com.kurogame.wutheringwaves.global
Game version: 3.3.x
Launcher compatibility: WUWA Global 3.3
Supported game version: 3.3

Preset write policy:
Safe: WRITE_ENABLED
Balanced: WRITE_ENABLED
Performance: WRITE_ENABLED
Max Graphics: LOCKED

Shizuku: READY

Patch state: PATCHED
Config state: BALANCED
Trusted backup: true
PAK exists: true
MountLang exists: true
MountLang points to PAK: true
Engine.ini readable: true
DeviceProfiles.ini readable: true

Actions:
Install Patch: false
Apply Safe: true
Apply Balanced: true
Apply Performance: true
Remove Patch: true
Restore Original: true
Backup Configs: true
Download Patch: true
Hint: Vietnamese patch appears installed. Safe, Balanced, Performance, Remove, or Restore is available.
Last action: Balanced preset applied
```

## Yêu Cầu

- Android 11 trở lên
- Đã cài Wuthering Waves bản Global
- Đã cài và bật Shizuku
- Còn đủ dung lượng trống để tải patch và tạo backup

## Supported Game Versions

- Wuthering Waves Global
- Android package: `com.kurogame.wutheringwaves.global`
- Supported game version: `3.3`
- Mục tiêu test: Android 11 / 12 / 13 / 14 / 15

## Cách Cài

1. Tải file APK release từ [GitHub Releases](https://github.com/Acceleratorer/WUWA-VN-Android/releases).
2. Chỉ cài file `WUWA-VN-vX.Y.Z-release.apk`, không cài Source code zip/tar.gz.
3. Dùng Android 11 trở lên. Nếu test bằng BlueStacks, tạo instance Android 11 64-bit.
4. Nếu Android hỏi, cho phép Install unknown apps cho browser hoặc file manager đang dùng để mở APK.
5. Nếu cài đặt fail, gỡ bản WUWA VN cũ rồi cài lại APK release.
6. Mở Shizuku và bật dịch vụ bằng Wireless Debugging.
7. Cấp quyền Shizuku cho WUWA VN.
8. Mở WUWA VN và bấm **Install Help** hoặc **Show Setup Guide** nếu cần hướng dẫn từng bước.
9. Bấm **Show Patch Plan**.
10. Kiểm tra danh sách file sẽ thay đổi.
11. Bấm **Backup Game Configs** để copy read-only `Engine.ini`, `DeviceProfiles.ini`, `MountLang_en.txt` vào backup.
12. Bấm **Download & Verify Patch** để tải PAK và kiểm tra SHA-256.
13. Bấm **Install Vietnamese Patch** để xem patch write dry-run, xác nhận hai lần, rồi cài `WuWaVH_99_P.pak`.
14. Bấm **Apply Safe Config Preset** để xem config dry-run, xác nhận hai lần, rồi ghi `Engine.ini`, `DeviceProfiles.ini`, `MountLang_en.txt` bằng template bundled.
15. Bấm **Apply Balanced Preset** sau khi state là PATCHED nếu muốn xem Balanced dry-run, xác nhận hai lần, rồi ghi `Engine.ini`, `DeviceProfiles.ini`, `MountLang_en.txt` bằng template bundled.
16. Bấm **Apply Performance Preset** sau khi state là PATCHED nếu muốn xem Performance dry-run, xác nhận hai lần, rồi ghi `Engine.ini`, `DeviceProfiles.ini`, `MountLang_en.txt` bằng template bundled.
17. Bấm **Remove Vietnamese Patch** nếu muốn rollback PAK: app sẽ restore `MountLang_en.txt` từ backup VERIFIED, xác nhận hai lần, xoá đúng `WuWaVH_99_P.pak`, rồi verify PAK không còn tồn tại.
18. Bấm **Restore Original Files** nếu cần khôi phục file gốc từ backup VERIFIED.
19. Bấm **Recovery Guide** nếu state là PARTIAL hoặc UNKNOWN để xem hướng dẫn khôi phục an toàn.

## Verify APK

Sau khi tải APK, so sánh SHA-256 của APK với file `sha256.txt` trong GitHub Releases.

Không cài APK từ mirror lạ, link chat riêng, hoặc file không có SHA-256 đi kèm.

Ví dụ file phát hành hợp lệ:

```text
WUWA-VN-v3.3.18-release.apk
```

Không phát hành file `app-debug.apk` cho người dùng phổ thông.

Trước khi phát hành, kiểm tra chữ ký:

```bash
apksigner verify --print-certs WUWA-VN-v3.3.18-release.apk
```

## Cách Khôi Phục

Mở app, chọn **Restore Original Files**, chọn bản backup muốn kiểm tra, xem restore dry-run, rồi xác nhận hai lần nếu muốn restore file gốc.

Backup được lưu trong thư mục app-specific external storage để tránh xin quyền lưu trữ rộng:

```text
Android/data/com.acceleratorer.wuwavn/files/WUWA-VH-Backup/
  2026-05-15_22-30-10/
    Engine.ini
    DeviceProfiles.ini
    MountLang_en.txt
    metadata.json
```

Restore chỉ được mở khi backup thoả tất cả điều kiện:

- `backup_type` là `shizuku_read_only_config_backup`
- `game_package` là `com.kurogame.wutheringwaves.global`
- `restore_write_enabled` trong metadata là `false`
- Cả 3 file `Engine.ini`, `DeviceProfiles.ini`, `MountLang_en.txt` đều VERIFIED
- Shizuku đang READY
- Wuthering Waves Global được phát hiện

Từ `v3.3.9`, app mở khóa Performance write cho Wuthering Waves Global `3.3`. PAK-only patch write, Remove PAK write, Safe / Default config preset write, Balanced config preset write, và Performance config preset write được mở khóa, nhưng Balanced/Performance chỉ được ghi khi state là PATCHED. Max Graphics đang khóa.

## Các Chế Độ Cấu Hình

- **Safe / Default**: ít thay đổi nhất, ổn định nhất, không bật CVars đồ hoạ nặng.
- **Balanced**: đã mở khóa write, chỉ dùng conservative CVars, không unlock FPS/Vulkan/resolution cực đoan. Từ `v3.3.6`, chỉ apply khi state là PATCHED.
- **Performance**: đã mở khóa write ở `v3.3.9`, giảm một số quality CVars theo hướng conservative, không unlock FPS/Vulkan/resolution cực đoan. Chỉ apply khi state là PATCHED.
- **Max Graphics**: đang khóa, cấu hình nặng, có thể gây nóng máy, hao pin, crash hoặc tụt FPS. Chỉ nên dùng với máy mạnh.

Mặc định nên dùng **Safe / Default**.

## Shizuku Dùng Để Làm Gì?

WUWA VN cần Shizuku để truy cập đúng thư mục dữ liệu của game trên Android mới. Ứng dụng chỉ nên chỉnh sửa những file nằm trong allowlist của Wuthering Waves, không nhận đường dẫn tuỳ ý từ người dùng.

Các trạng thái Shizuku cần kiểm tra trong app:

- Shizuku chưa được cài
- Shizuku đã cài nhưng chưa chạy
- Shizuku đang chạy nhưng chưa cấp quyền
- Sẵn sàng

## Cập Nhật App

Hiện tại app mở GitHub Releases để người dùng tự tải bản mới. Manifest không xin quyền `REQUEST_INSTALL_PACKAGES`.

Ứng dụng không tự cài APK âm thầm.

## Known Issues

- Shizuku chưa chạy
- Chưa cấp quyền Shizuku cho app
- Game vẫn đang mở và file có thể bị khoá
- Không đủ dung lượng để tạo backup
- Android chặn cài APK từ nguồn không xác định
- Max Graphics vẫn khóa
- Balanced bị block khi state là ORIGINAL, PARTIAL hoặc UNKNOWN
- Performance bị block khi state là ORIGINAL, PARTIAL hoặc UNKNOWN
- Remove Vietnamese Patch cần backup VERIFIED để restore `MountLang_en.txt` trước khi xoá PAK
- Khi state là UNKNOWN, các action nguy hiểm sẽ bị tắt để tránh ghi/xoá sai trạng thái

## Release Line 3.3

- v3.3.6: Stability release, Balanced chỉ apply khi state là PATCHED.
- v3.3.7: QA hardening release, thêm diagnostics snapshot, recovery guidance, và test checklist.
- v3.3.8: Performance preview release, thêm Performance dry-run only và giữ Performance write khóa.
- v3.3.9: Performance write release, chỉ apply khi state là PATCHED và có trusted backup.
- v3.3.10: WUWA Global 3.3 LTS release, thêm Recovery Guide, preset policy trong snapshot, và checklist test LTS.
- v3.3.11: Gradle migration release, không đổi app behavior.
- v3.3.12: GitHub Actions release automation, build/sign/upload APK tự động.
- v3.3.13: Low-tech onboarding polish, thêm setup guide/checklist/Shizuku help, không đổi app behavior.
- v3.3.14: Release hardening, CI verify APK SHA/version/package/permission/debuggable.
- v3.3.15: Compose preview foundation, thêm preview-only Compose screens, không đổi runtime UI/write logic.
- v3.3.16: Compose home screen parity, màn chính runtime dùng Compose nhưng giữ controller/write flow cũ.
- v3.3.17: Non-tech install polish, thêm Install Help và hướng dẫn APK/Android 11+/BlueStacks/unknown-source rõ hơn.
- v3.3.18: Compose diagnostics/snapshot polish, hiển thị summary và snapshot preview rõ hơn trên màn chính.

## Roadmap

- v3.4.0: WUWA Global 3.4 compatibility release

Source app hiện đã được migrate sang Kotlin. Từ `v3.3.11`, Gradle là build path chính và vẫn dùng `version.properties` làm source of truth cho app version. Từ `v3.3.12`, GitHub Actions có thể build release APK khi tạo GitHub Release tag mới.
Từ `v3.3.17`, màn chính runtime dùng Jetpack Compose nhưng vẫn giữ các controller, Shizuku flow, và write logic hiện có. Từ `v3.3.18`, màn chính Compose hiển thị thêm Diagnostics Summary và State Snapshot Preview.

## Build From Source

Từ `v3.3.11`, repo hỗ trợ Gradle build.

Debug build:

```bash
./gradlew :app:assembleDebug
```

Release build local:

```bash
./gradlew :app:assembleRelease
```

Compose home screen:

```text
app/src/main/kotlin/com/acceleratorer/wuwavn/ComposeHomeScreen.kt
```

Mở file này trong Android Studio để xem `@Preview` cho home/status/setup/diagnostics/snapshot/preset policy. Runtime `MainActivity` cũng dùng Compose screen này từ `v3.3.17`.

Từ `v3.3.12`, GitHub Actions có thể build release APK khi tạo GitHub Release tag mới.

Release artifact gồm:

```text
WUWA-VN-vX.Y.Z-release.apk
sha256.txt
release-verification-report.txt
```

## Release Verification

Từ `v3.3.14`, release APK được verify bằng script:

```bash
python tools/verify-release-apk.py WUWA-VN-v3.3.18-release.apk
```

Script kiểm tra:

- SHA-256 APK khớp `update.json`
- `versionName` và `versionCode` khớp `version.properties`
- package là `com.acceleratorer.wuwavn`
- release APK không debuggable
- APK không xin `REQUEST_INSTALL_PACKAGES`

Manual PowerShell pipeline vẫn được giữ tạm thời làm legacy fallback:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build-release.ps1
```

Gradle là build path chính từ `v3.3.11`. Legacy script vẫn tự kiểm tra Android SDK build-tools `36.0.0`, download và verify Shizuku AARs, download và verify Kotlin compiler `2.0.21`, compile AIDL/Java generated sources/Kotlin sources, chạy D8, zipalign, apksigner, rồi xuất APK vào `release/`.

GitHub Actions release secrets cần cấu hình:

```text
WUWA_RELEASE_KEYSTORE_BASE64
WUWA_RELEASE_STORE_PASSWORD
WUWA_RELEASE_KEY_ALIAS
WUWA_RELEASE_KEY_PASSWORD
```

Sau khi CI tạo `sha256.txt`, cập nhật `update.json` bằng SHA-256 thật của APK release.

## Báo Lỗi

Khi gặp lỗi, hãy bấm **Copy State Snapshot** hoặc **Send Issue Report** trước, rồi gửi kèm log trong app nếu có:

```text
[22:31:10] App version: 3.3.13
[22:31:10] Android version: 14
[22:31:11] Shizuku: running
[22:31:11] Permission: granted
[22:31:12] Game folder: found
[22:31:12] Game version: 3.3.x
[22:31:12] Launcher compatibility: WUWA Global 3.3
[22:31:13] State: patch=PATCHED, config=SAFE_DEFAULT
[22:31:13] State: pak=true, mountLang=true
[22:31:13] State: trustedBackup=true
[22:31:13] Smart UI: Vietnamese patch appears installed. Remove or restore is available.
[22:31:15] Backup read: copied Engine.ini
[22:31:16] Backup read: copied DeviceProfiles.ini
[22:31:16] Backup read: copied MountLang_en.txt
[22:31:16] Backup metadata: wrote actual backed-up files
[22:31:16] Restore dry run: verified 3/3 files
[22:31:17] Restore write: verified Engine.ini
[22:31:18] Restore write: success
[22:31:19] Patch download: success
[22:31:19] SHA-256: verified
[22:31:20] Patch write: started
[22:31:25] Patch write: verified
[22:31:26] Safe config preset: started
[22:31:27] Safe preset write: verified Engine.ini
[22:31:28] Safe config preset: success
[22:31:29] Balanced preset dry run: shown
[22:31:30] Balanced preset: started
[22:31:31] Balanced write: verified Engine.ini
[22:31:32] Balanced preset: success
[22:31:33] Patch remove: MountLang restored
[22:31:34] Patch remove: verified target deleted
```

## Test Checklist 3.3

Xem checklist test LTS tại [`docs/WUWA-3.3-LTS-TEST-CHECKLIST.md`](docs/WUWA-3.3-LTS-TEST-CHECKLIST.md).

- [ ] ORIGINAL: Install enabled nếu có trusted backup, Safe enabled nếu có trusted backup, Balanced disabled, Performance disabled, Remove disabled, Restore enabled nếu có trusted backup
- [ ] PATCHED: Install disabled, Safe enabled nếu có trusted backup, Balanced enabled nếu có trusted backup, Performance enabled nếu có trusted backup, Remove enabled nếu có trusted backup, Restore enabled nếu có trusted backup
- [ ] PARTIAL: Install disabled, Safe disabled, Balanced disabled, Performance disabled, Remove/Restore enabled nếu có trusted backup
- [ ] UNKNOWN: dangerous actions disabled, Performance disabled
- [ ] First install shows setup guide, **Got it** hides it next launch
- [ ] **Show Setup Guide** opens onboarding again
- [ ] Setup Checklist shows correct YES/NO for install and presets
- [ ] Shizuku Setup Help explains the current Shizuku state
- [ ] Open Developer Options works or opens Shizuku setup guide fallback
- [ ] Home actions are grouped into Setup, Patch, Config Presets, and Diagnostics
- [ ] Backup copy đúng `Engine.ini`, `DeviceProfiles.ini`, `MountLang_en.txt` và ghi metadata
- [ ] Download verify PAK SHA-256 trước khi install
- [ ] Install Vietnamese Patch chỉ ghi `WuWaVH_99_P.pak`
- [ ] Safe / Default chỉ ghi 3 file config allowlisted
- [ ] Balanced chỉ ghi 3 file config khi state là PATCHED
- [ ] Performance show dry-run/final confirmation và chỉ ghi 3 file config khi state là PATCHED
- [ ] Remove Patch restore `MountLang_en.txt`, xoá PAK, rồi verify target deleted
- [ ] Restore Original Files chỉ restore backup VERIFIED
- [ ] Max Graphics vẫn khóa
- [ ] Manifest không thêm permission mới và `android:debuggable=false`

## Security Checklist

- [ ] APK là release-signed, không phải debug-signed
- [ ] `android:debuggable=false`
- [ ] `update.json` có SHA-256 thật cho APK
- [ ] APK URL trỏ về `Acceleratorer/WUWA-VN-Android`
- [ ] PAK SHA-256 là hash thật
- [ ] App kiểm tra SHA-256 trước khi cài hoặc áp dụng file
- [ ] Backup chạy trước mọi thay đổi file
- [ ] Restore đã được test
- [ ] Các trạng thái Shizuku được xử lý an toàn
- [ ] Shizuku permission check dùng API thật, không chỉ kiểm tra package
- [ ] App chỉ chỉnh sửa file trong allowlist
- [ ] Release notes/changelog rõ ràng
- [ ] Không commit keystore hoặc signing secret

## Credits

- Vietnamese translation pack: [CallMeDangDev/WuwaVH](https://github.com/CallMeDangDev/WuwaVH)
- Android patch manager: [Acceleratorer/WUWA-VN-Android](https://github.com/Acceleratorer/WUWA-VN-Android)

## License

MIT License. Xem file [LICENSE](LICENSE).

## Lưu Ý

Ứng dụng không liên kết với Kuro Games.

Hãy sao lưu trước khi sử dụng.

Không dùng app để gian lận, bypass anti-cheat, hoặc can thiệp gameplay online.
