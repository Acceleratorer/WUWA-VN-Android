# WUWA Việt Hoá Android

Ứng dụng hỗ trợ cài bản Việt hoá cho Wuthering Waves trên Android.

> Trạng thái hiện tại: bản `v2.6.0` là APK release-signed sạch, source app đã được rebuild sang Kotlin, có kiểm tra game/Shizuku, backup read-only, verify SHA-256, restore write cho backup gốc đã VERIFIED, và cài patch PAK đầu tiên. App chỉ ghi `WuWaVH_99_P.pak` sau khi có backup tin cậy, PAK đã verify, Shizuku READY, và hai bước xác nhận. Ghi config preset vẫn đang khóa.

## Tính Năng

- Chuẩn bị luồng cài bản Việt hoá cho Wuthering Waves bản Global
- Cập nhật bản dịch mới nhất từ GitHub Releases
- Sao lưu read-only các file cấu hình gốc trước khi chỉnh sửa
- Restore dry-run: liệt kê backup, verify metadata/SHA-256, show restore plan
- Restore file gốc từ backup đã VERIFIED bằng Shizuku
- Cài bản Việt hoá PAK-only: chỉ ghi `WuWaVH_99_P.pak`
- Hỗ trợ Shizuku để thao tác với thư mục game
- Hỗ trợ cấu hình đồ hoạ: Safe, Balanced, Performance, Max Graphics
- Tải PAK vào app storage và kiểm tra SHA-256 trước khi cho phép bước tiếp theo
- Ghi `metadata.json` với danh sách file backup thật, dung lượng và SHA-256
- Copy đường dẫn backup để dễ gửi log hoặc tự kiểm tra
- Copy debug log để gửi báo lỗi

## Screenshots

| Home | Shizuku Check | Apply Patch | Restore |
|---|---|---|---|
| Sẽ bổ sung sau khi test trên máy thật | Sẽ bổ sung sau khi test trên máy thật | Sẽ bổ sung sau khi test trên máy thật | Sẽ bổ sung sau khi test trên máy thật |

## Yêu Cầu

- Android 11 trở lên
- Đã cài Wuthering Waves bản Global
- Đã cài và bật Shizuku
- Còn đủ dung lượng trống để tải patch và tạo backup

## Supported Game Versions

- Wuthering Waves Global
- Android package: `com.kurogame.wutheringwaves.global`
- Mục tiêu test: Android 11 / 12 / 13 / 14 / 15

## Cách Cài

1. Tải file APK release từ [GitHub Releases](https://github.com/Acceleratorer/WUWA-VN-Android/releases).
2. Cài đặt ứng dụng trên điện thoại.
3. Mở Shizuku và bật dịch vụ bằng Wireless Debugging.
4. Cấp quyền Shizuku cho WUWA VN.
5. Mở WUWA VN.
6. Bấm **Show Patch Plan**.
7. Kiểm tra danh sách file sẽ thay đổi.
8. Bấm **Backup Game Configs** để copy read-only `Engine.ini`, `DeviceProfiles.ini`, `MountLang_en.txt` vào backup.
9. Bấm **Download & Verify Patch** để tải PAK và kiểm tra SHA-256.
10. Bấm **Install Vietnamese Patch** để xem patch write dry-run, xác nhận hai lần, rồi cài `WuWaVH_99_P.pak`.
11. Bấm **Restore Original Files** nếu cần khôi phục file gốc từ backup VERIFIED.

## Verify APK

Sau khi tải APK, so sánh SHA-256 của APK với file `sha256.txt` trong GitHub Releases.

Không cài APK từ mirror lạ, link chat riêng, hoặc file không có SHA-256 đi kèm.

Ví dụ file phát hành hợp lệ:

```text
WUWA-VN-v2.6.0-release.apk
```

Không phát hành file `app-debug.apk` cho người dùng phổ thông.

Trước khi phát hành, kiểm tra chữ ký:

```bash
apksigner verify --print-certs WUWA-VN-v2.6.0-release.apk
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

Từ `v2.6.0`, app chỉ mở khóa PAK-only patch write. Ghi config preset vẫn đang khóa tới `v2.7.0`.

## Các Chế Độ Cấu Hình

- **Safe / Default**: chỉ cài bản dịch, ít thay đổi nhất, phù hợp với mọi máy.
- **Balanced**: bản dịch kèm cấu hình ổn định, phù hợp Snapdragon 865 / 870 / 778G / Dimensity 8100 trở lên.
- **Performance**: bản dịch kèm cấu hình giảm giật, phù hợp máy tầm trung hoặc yếu.
- **Max Graphics**: cấu hình nặng, có thể gây nóng máy, hao pin, crash hoặc tụt FPS. Chỉ nên dùng với máy mạnh.

Mặc định nên dùng **Safe / Default** hoặc **Balanced**.

## Shizuku Dùng Để Làm Gì?

WUWA VN cần Shizuku để truy cập đúng thư mục dữ liệu của game trên Android mới. Ứng dụng chỉ nên chỉnh sửa những file nằm trong allowlist của Wuthering Waves, không nhận đường dẫn tuỳ ý từ người dùng.

Các trạng thái Shizuku cần kiểm tra trong app:

- Shizuku chưa được cài
- Shizuku đã cài nhưng chưa chạy
- Shizuku đang chạy nhưng chưa cấp quyền
- Sẵn sàng

## Quyền Cài APK Dùng Để Làm Gì?

Quyền yêu cầu cài APK chỉ dùng khi người dùng chọn cập nhật app từ GitHub Releases. App phải hiển thị phiên bản mới, changelog, dung lượng tải về và kết quả kiểm tra SHA-256 trước khi mở màn hình cài đặt của Android.

Ứng dụng không nên tự động cài đặt âm thầm.

## Known Issues

- Shizuku chưa chạy
- Chưa cấp quyền Shizuku cho app
- Game vẫn đang mở và file có thể bị khoá
- Không đủ dung lượng để tạo backup
- Android chặn cài APK từ nguồn không xác định
- Bản `v2.6.0` chỉ cài PAK Việt hoá; ghi config preset vẫn khóa

## Roadmap

- `v2.7.0`: config preset writing, bắt đầu với Safe / Balanced.

Source app hiện đã được migrate sang Kotlin. Build script vẫn là manual pipeline nhẹ, có download Kotlin compiler `2.0.21` từ JetBrains và verify SHA-256 trước khi compile.

## Build From Source

Repo hiện chưa dùng Gradle. Build release bằng manual Android pipeline:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build-release.ps1
```

Script sẽ tự kiểm tra Android SDK build-tools `36.0.0`, download và verify Shizuku AARs, download và verify Kotlin compiler `2.0.21`, compile AIDL/Java generated sources/Kotlin sources, chạy D8, zipalign, apksigner, rồi xuất APK vào `release/`.

## Báo Lỗi

Khi gặp lỗi, hãy gửi kèm log trong app nếu có:

```text
[22:31:10] App version: 2.6.0
[22:31:10] Android version: 14
[22:31:11] Shizuku: running
[22:31:11] Permission: granted
[22:31:12] Game folder: found
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
```

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
