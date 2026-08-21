# WUWA Việt Hoá Android

Ứng dụng hỗ trợ cài bản Việt hoá cho Wuthering Waves trên Android.

> Trạng thái hiện tại: bản `v3.6.0` đã port sang WUWA Global 3.6. Patch dùng `Saved/Resources/<resource-version>/Lang_en/<lang-version>/WuWaVH_99_P.pak` + `.sig` và registry sáu trường trong `Saved/Resources/<resource-version>/Mount/MountLang_en.txt`. Install/update và remove là transaction PAK/SIG/MountLang; preset config, Restore Original Files write, root write và Max Graphics vẫn khóa cho đến khi format/rollback được xác minh đầy đủ.

## Tính Năng

- Chuẩn bị luồng cài bản Việt hoá cho Wuthering Waves bản Global
- Cập nhật bản dịch mới nhất từ GitHub Releases
- Sao lưu read-only các file cấu hình gốc trước khi chỉnh sửa
- Restore dry-run: liệt kê backup, verify metadata/SHA-256, show restore plan
- Restore dry-run từ backup đã VERIFIED; restore write tổng quát đang khóa trong 3.6
- Cài/cập nhật patch bằng transaction: ghi `WuWaVH_99_P.pak`, SIG đi kèm và MountLang registry
- Hỗ trợ Shizuku để thao tác với thư mục game
- Hiển thị compatibility với Wuthering Waves Global `3.6`
- Smart State Detection: nhận diện Original / Patched / Partial / Unknown
- Hiển thị trạng thái PAK, `MountLang_en.txt`, Safe config, trusted backup và recommended action
- UI tự bật/tắt action theo trạng thái để tránh bấm đè hoặc thao tác sai
- Hiển thị dry-run cho Safe / Balanced / Performance nhưng các preset write bị khóa trong 3.6
- Remove Vietnamese Patch: restore Resources MountLang từ trusted original backup, xoá đúng PAK + SIG, rồi verify transaction
- Chuẩn bị cấu hình đồ hoạ theo lộ trình: preset config và Max Graphics vẫn khóa
- Header UI dùng artwork bundled trong APK, không tải ảnh từ mạng
- Icon app dùng artwork bundled trong APK
- Jetpack Compose home screen: màn chính runtime dùng Compose nhưng vẫn gọi controller/write flow cũ
- Compose diagnostics polish: state snapshot và issue report vẫn có app/game/Shizuku/patch/backup/hint summary, preset policy, và action state
- Non-tech home screen: chỉ giữ What To Do Now, Setup Checklist, và 6 Quick Actions chính gồm Start Setup, Open Shizuku, Backup, Download, Install, và More Tools
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
- Optional Root Backend Preview cho máy đã root: chỉ check root thủ công, không ghi file bằng root
- Root Preview Help giải thích root bằng ngôn ngữ dễ hiểu cho user không rành kỹ thuật
- Shortcut mở Developer Options, fallback sang Shizuku setup guide nếu ROM không hỗ trợ
- More Tools gom các flow nâng cao: patch plan, presets, remove/restore, recovery, diagnostics, root preview, và install help
- Game Path Diagnostic read-only trong More Tools để xác nhận layout WUWA 3.6 động, Lang_en PAK/SIG, SHA-256/preview ngắn của MountLang, và Patch Plan Preview read-only

## Screenshots

| ORIGINAL | PATCHED | PARTIAL | UNKNOWN / Shizuku |
|---|---|---|---|
| Cần chụp state gốc: PAK missing, MountLang không trỏ PAK | Cần chụp state đã cài: PAK exists, MountLang trỏ PAK | Cần chụp state lệch: PAK/MountLang không khớp | Cần chụp khi Shizuku chưa READY hoặc không đọc được state |

## Apply State Matrix

| State | Install / Update Patch | Apply Safe / Default | Apply Balanced | Apply Performance | Remove Patch | Restore Original |
|---|---|---|---|---|---|---|
| ORIGINAL | Enabled if trusted original backup exists | Locked in 3.6 | Locked in 3.6 | Locked in 3.6 | Disabled | Locked in 3.6 |
| PATCHED | Enabled if trusted original backup exists | Locked in 3.6 | Locked in 3.6 | Locked in 3.6 | Enabled if trusted original backup exists | Locked in 3.6 |
| PARTIAL | Disabled | Disabled | Disabled | Disabled | Enabled if trusted original backup exists | Locked in 3.6 |
| UNKNOWN | Disabled | Disabled | Disabled | Disabled | Disabled | Disabled |

## Diagnostics Snapshot

Nút **Copy State Snapshot** copy thông tin ngắn gọn để debug issue report:

```text
WUWA VN State Snapshot
App version: 3.6.0 (66)
Game package: com.kurogame.wutheringwaves.global
Game version: 3.6.x
Launcher compatibility: WUWA Global 3.6
Supported game version: 3.6

Preset write policy:
Safe: LOCKED
Balanced: LOCKED
Performance: LOCKED
Max Graphics: LOCKED

Root backend preview: Root preview not checked
Root write enabled: false

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
Apply Safe: false
Apply Balanced: false
Apply Performance: false
Remove Patch: true
Restore Original: false
Backup Configs: true
Download Patch: true
Hint: Vietnamese patch appears installed. Update/remove is available; config preset and restore writes are locked for WUWA 3.6.
Last action: State refreshed
```

## Yêu Cầu

- Android 11 trở lên
- Đã cài Wuthering Waves bản Global
- Đã cài và bật Shizuku
- Còn đủ dung lượng trống để tải patch và tạo backup
- Root là tuỳ chọn nâng cao, không bắt buộc. Root chỉ là preview check thủ công và không có root write.

## Supported Game Versions

- Wuthering Waves Global
- Android package: `com.kurogame.wutheringwaves.global`
- Supported game version: `3.6`
- Mục tiêu test: Android 11 / 12 / 13 / 14 / 15

## Cách Cài

1. Tải file APK release từ [GitHub Releases](https://github.com/Acceleratorer/WUWA-VN-Android/releases).
2. Chỉ cài file `WUWA-VN-vX.Y.Z-release.apk`, không cài Source code zip/tar.gz.
3. Dùng Android 11 trở lên. Nếu test bằng BlueStacks, tạo instance Android 11 64-bit.
4. Nếu Android hỏi, cho phép Install unknown apps cho browser hoặc file manager đang dùng để mở APK.
5. Nếu cài đặt fail, gỡ bản WUWA VN cũ rồi cài lại APK release.
6. Mở Shizuku và bật dịch vụ bằng Wireless Debugging.
7. Cấp quyền Shizuku cho WUWA VN.
8. Mở WUWA VN và bấm **Start Setup** nếu cần hướng dẫn từng bước.
9. Bấm **Open Shizuku** nếu Shizuku chưa chạy hoặc chưa cấp quyền.
10. Bấm **Backup Game Configs** để copy read-only `Engine.ini`, `DeviceProfiles.ini` và Resources MountLang động vào backup.
11. Bấm **Download & Verify Patch** để tải PAK và kiểm tra SHA-256.
12. Chỉ bấm **Install Vietnamese Patch** nếu nút được bật. Nếu nút vẫn khóa, bấm **More Tools > Game Path Diagnostic** và gửi report trước khi thử cài.
13. Bấm **More Tools** nếu cần thao tác nâng cao như Install Help, Game Path Diagnostic, Show Patch Plan, Remove Patch, restore dry-run, preset dry-run, Recovery Guide, Copy State Snapshot, hoặc Root Preview. Config preset write và Restore Original Files write đang khóa trong 3.6.
14. Nếu máy đã root và muốn kiểm tra thử, mở **More Tools** rồi bấm **Root Preview Help** trước, sau đó mới bấm **Check Root Access**. Bước này không ghi file.

## Verify APK

Sau khi tải APK, so sánh SHA-256 của APK với file `sha256.txt` trong GitHub Releases.

Không cài APK từ mirror lạ, link chat riêng, hoặc file không có SHA-256 đi kèm.

Ví dụ file phát hành hợp lệ:

```text
WUWA-VN-v3.6.0-release.apk
```

Không phát hành file `app-debug.apk` cho người dùng phổ thông.

Trước khi phát hành, kiểm tra chữ ký:

```bash
 apksigner verify --print-certs WUWA-VN-v3.6.0-release.apk
```

## Cách Khôi Phục

Mở app, chọn **Restore Original Files** để xem backup và dry-run. Restore write tổng quát đang khóa trong 3.6 vì chưa có transaction rollback cho cả ba file config.

Backup được lưu trong thư mục app-specific external storage để tránh xin quyền lưu trữ rộng:

```text
Android/data/com.acceleratorer.wuwavn/files/WUWA-VH-Backup/
  2026-05-15_22-30-10/
    Engine.ini
    DeviceProfiles.ini
    MountLang_en.Resources-3.6.x.txt
    metadata.json
```

Restore chỉ được mở khi backup thoả tất cả điều kiện:

- `backup_type` là `shizuku_read_only_config_backup`
- `game_package` là `com.kurogame.wutheringwaves.global`
- `restore_write_enabled` trong metadata là `false`
- Cả 3 file `Engine.ini`, `DeviceProfiles.ini` và Resources MountLang động đều VERIFIED
- Shizuku đang READY để đọc dry-run
- Wuthering Waves Global được phát hiện
- Resources MountLang phải đúng format sáu trường và không được chứa entry `Lang_en/*/WuWaVH_99_P.pak`

Trong `v3.6.0`, app mở khóa install/update và remove transaction cho WUWA Global `3.6`. Safe / Default, Balanced, Performance, Restore Original Files write, root write và Max Graphics đều đang khóa; dry-run vẫn có để kiểm tra kế hoạch.

## Các Chế Độ Cấu Hình

- **Safe / Default, Balanced, Performance**: chỉ preview/dry-run trong 3.6; chưa ghi config cho đến khi format config mới được xác minh.
- **Max Graphics**: đang khóa vĩnh viễn trong release này, vì có thể gây nóng máy, hao pin, crash hoặc tụt FPS.

Mặc định nên giữ config game hiện tại và chỉ dùng patch transaction đã verify.

## Shizuku Dùng Để Làm Gì?

WUWA VN cần Shizuku để truy cập đúng thư mục dữ liệu của game trên Android mới. Ứng dụng chỉ nên chỉnh sửa những file nằm trong allowlist của Wuthering Waves, không nhận đường dẫn tuỳ ý từ người dùng.

Các trạng thái Shizuku cần kiểm tra trong app:

- Shizuku chưa được cài
- Shizuku đã cài nhưng chưa chạy
- Shizuku đang chạy nhưng chưa cấp quyền
- Sẵn sàng

## Optional Root Backend Preview

Từ `v3.3.19`, app có **Root Backend Preview** cho máy đã root.

- **Root Preview Help**: giải thích root bằng ngôn ngữ dễ hiểu.
- **Check Root Access**: chỉ chạy check thủ công `su -c id` sau khi user xác nhận.
- Nếu Magisk/root manager hiện popup, user có thể deny và tiếp tục dùng Shizuku.
- Root write vẫn khóa trong `v3.3.19`.
- Backup/install/remove/restore/config preset vẫn dùng Shizuku flow hiện tại.

Root preview không thêm permission mới, không nhận path tuỳ ý, không mở Max Graphics, và không bỏ qua SHA-256.

## Cập Nhật App

Hiện tại app mở GitHub Releases để người dùng tự tải bản mới. Manifest không xin quyền `REQUEST_INSTALL_PACKAGES`.

Ứng dụng không tự cài APK âm thầm.

## Known Issues

- Shizuku chưa chạy
- Chưa cấp quyền Shizuku cho app
- Game vẫn đang mở và file có thể bị khoá
- Không đủ dung lượng để tạo backup
- Android chặn cài APK từ nguồn không xác định
- Root manager popup bị deny hoặc timeout khi bấm Check Root Access
- Max Graphics vẫn khóa
- Preset config writes và general Restore Original Files write đang khóa trong 3.6
- Remove Vietnamese Patch cần trusted original backup VERIFIED để restore Resources MountLang trước khi xoá PAK + SIG
- Backup có MountLang đã chứa patch registry sẽ không được coi là original trusted backup
- Khi state là UNKNOWN, các action nguy hiểm sẽ bị tắt để tránh ghi/xoá sai trạng thái

## Release 3.6

- `v3.6.0`: port WUWA Global layout to dynamic `Saved/Resources/<resource-version>/Lang_en/<lang-version>` targets.
- `v3.6.0`: install/update now stages and verifies PAK + SIG + six-field Resources MountLang as one transaction.
- `v3.6.0`: remove now restores trusted original Resources MountLang and removes the PAK/SIG pair transactionally.
- `v3.6.0`: reject patched MountLang files as original backups, tighten SIG pairing, bound download redirects, and preserve rollback errors.
- `v3.6.0`: keep config preset writes, general restore writes, root writes, and Max Graphics locked pending further 3.6 validation.

## Historical Release Line 3.3

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
- v3.3.19: Non-tech home simplification + Optional Root Backend Preview, màn chính chỉ hiện phần cần thiết và root write vẫn khóa.
- v3.3.20: Android 3.3.2 path diagnostic, thêm Game Path Diagnostic read-only và không đổi write behavior.
- v3.3.21: Game Path Diagnostic hotfix, dùng lại Shizuku backup service read-only để tránh timeout.
- v3.3.22: Game Path Diagnostic binding hotfix, tránh background state refresh và retry Shizuku backup service.
- v3.3.23: Game Path Diagnostic shell fallback hotfix, đọc path bằng Shizuku shell allowlist nếu user service bind timeout.
- v3.3.24: Game Path Diagnostic shell process hotfix, sửa lỗi fallback báo `process hasn't exited`.
- v3.3.25: Android 3.3.2 layout confirmation, report Resources layout confirmed và MountLang SHA/preview read-only.
- v3.3.26: Android 3.3.2 Patch Plan Preview, report proposed Resources PAK/SIG targets và giữ writer khóa.
- v3.3.27: Patch SHA refresh, pin WuwaVH `3.3.6` PAK URL và cập nhật SHA-256 để Download & Verify Patch pass lại.
- v3.3.28: Patch verified guidance hotfix, nói rõ Install chỉ dùng khi nút được bật và Android 3.3.2 writer vẫn khóa.
- v3.3.29: MountLang SHA-1 probe, xác nhận official PAK/SIG hash format và proposed mount order trong diagnostic read-only.
- v3.3.30: Backup shell fallback hotfix, Backup Game Configs tránh timeout user-service và vẫn không mở writer Android 3.3.2.
- v3.3.31: Android 3.3.2 backup summary polish, Resources MountLang backup hiển thị OK khi legacy MountLang path missing là expected.

## Roadmap

- v3.6.x: validate config formats and implement transactional config restore before unlocking preset/general restore writes.

Source app hiện đã được migrate sang Kotlin. Từ `v3.3.11`, Gradle là build path chính và vẫn dùng `version.properties` làm source of truth cho app version. Từ `v3.3.12`, GitHub Actions có thể build release APK khi tạo GitHub Release tag mới.
Từ `v3.3.17`, màn chính runtime dùng Jetpack Compose nhưng vẫn giữ các controller, Shizuku flow, và write logic hiện có. Từ `v3.3.18`, màn chính Compose có Diagnostics Summary và State Snapshot Preview. Từ `v3.3.19`, màn chính ưu tiên user phổ thông với 6 Quick Actions, tool nâng cao nằm trong More Tools, và Root Backend Preview chỉ detect root thủ công chứ không mở root write. Từ `v3.3.31`, Backup Game Configs có Shizuku shell fallback khi user-service timeout và summary nói rõ Android 3.3.2 Resources MountLang backup OK nhưng writer vẫn khóa.

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

Ví dụ log lịch sử (format 3.3, chỉ để minh hoạ; không phải trạng thái runtime 3.6):

```text
app/src/main/kotlin/com/acceleratorer/wuwavn/ComposeHomeScreen.kt
```

Mở file này trong Android Studio để xem `@Preview` cho home/status/setup/diagnostics/snapshot/preset policy. Runtime `MainActivity` cũng dùng Compose screen này từ `v3.3.17`.

Từ `v3.3.12`, GitHub Actions có thể build release APK khi tạo GitHub Release tag mới.

Release artifact gồm:

```text
WUWA-VN-vX.Y.Z-release.apk
sha256.txt
update.json
release-verification-report.txt
```

## Release Verification

Từ `v3.3.14`, release APK được verify bằng script:

```bash
python tools/verify-release-apk.py WUWA-VN-v3.6.0-release.apk
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

Release workflow sẽ ghi SHA-256 của APK vừa build vào `update.json` artifact, rồi verify APK bằng `release-verification-report.txt`.

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
[22:31:13] Smart UI: Vietnamese patch appears installed. Update/remove available; config and restore writes locked for WUWA 3.6.
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

## Test Checklist 3.6

- [ ] ORIGINAL: install/update enabled only with a trusted original backup; preset, general restore, and Max Graphics writes locked.
- [ ] PATCHED: update/remove enabled only with a trusted original backup; preset and general restore writes locked.
- [ ] PARTIAL/UNKNOWN: dangerous actions remain disabled except remove when its exact trusted-original preconditions pass.
- [ ] Trusted backup rejects a MountLang containing any `Lang_en/*/WuWaVH_99_P.pak` entry.
- [ ] Installed PATCHED requires PAK + SIG + valid MountLang registry together.
- [ ] Install/update verifies PAK size/SHA-256, paired official SIG SHA-1, and MountLang hashes before commit.
- [ ] Install/update rollback restores all previous PAK/SIG/MountLang files after a simulated commit failure.
- [ ] Remove rollback restores all previous PAK/SIG/MountLang files after a simulated removal failure.
- [ ] Download follows only HTTPS trusted-host redirects and stops at the redirect limit.
- [ ] Legacy `Content/Paks` and `Config/Android/MountLang_en.txt` paths are rejected.
- [ ] `REQUEST_INSTALL_PACKAGES` is absent; root write and Max Graphics remain disabled.

## Historical Test Checklist 3.3

Xem checklist test LTS tại [`docs/WUWA-3.3-LTS-TEST-CHECKLIST.md`](docs/WUWA-3.3-LTS-TEST-CHECKLIST.md).

- [ ] ORIGINAL: Install enabled nếu có trusted backup, Safe enabled nếu có trusted backup, Balanced disabled, Performance disabled, Remove disabled, Restore enabled nếu có trusted backup
- [ ] PATCHED: Install disabled, Safe enabled nếu có trusted backup, Balanced enabled nếu có trusted backup, Performance enabled nếu có trusted backup, Remove enabled nếu có trusted backup, Restore enabled nếu có trusted backup
- [ ] PARTIAL: Install disabled, Safe disabled, Balanced disabled, Performance disabled, Remove/Restore enabled nếu có trusted backup
- [ ] UNKNOWN: dangerous actions disabled, Performance disabled
- [ ] First install shows setup guide, **Got it** hides it next launch
- [ ] **Start Setup** opens onboarding again
- [ ] Setup Checklist shows correct YES/NO for install and presets
- [ ] Shizuku Setup Help explains the current Shizuku state
- [ ] Root Preview Help explains root without enabling root writes
- [ ] Check Root Access asks for confirmation before calling root
- [ ] Root preview status appears in Setup Checklist and State Snapshot
- [ ] Open Developer Options works or opens Shizuku setup guide fallback
- [ ] Home screen shows 6 Quick Actions for non-tech users
- [ ] More Tools contains advanced actions: presets, remove/restore, recovery, diagnostics, root preview, and install help
- [ ] Game Path Diagnostic is read-only and reports candidate MountLang/Resources/PAK paths, layout confirmation, MountLang SHA/preview, and Android 3.3.2 Patch Plan Preview when available
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
- [ ] Root write remains disabled in v3.3.31

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
- [ ] Root preview không ghi file và không nhận path tuỳ ý
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
