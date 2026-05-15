# WUWA Việt Hoá Android

Ứng dụng hỗ trợ cài bản Việt hoá cho Wuthering Waves trên Android.

> Trạng thái hiện tại: bản `v2.3.0` là APK release-signed sạch, có kiểm tra game/Shizuku, kiểm tra binder và quyền Shizuku thật, dry run theo allowlist, tải PAK và kiểm tra SHA-256 thật. Bản này đã có backup read-only bằng Shizuku cho `Engine.ini`, `DeviceProfiles.ini`, `MountLang_en.txt` và ghi `metadata.json` từ file backup thật. Phần restore và ghi patch vào game vẫn đang khóa.

## Tính Năng

- Cài bản Việt hoá cho Wuthering Waves bản Global
- Cập nhật bản dịch mới nhất từ GitHub Releases
- Sao lưu read-only các file cấu hình gốc trước khi chỉnh sửa
- Khôi phục file gốc khi cần
- Hỗ trợ Shizuku để thao tác với thư mục game
- Hỗ trợ cấu hình đồ hoạ: Safe, Balanced, Performance, Max Graphics
- Tải PAK vào app storage và kiểm tra SHA-256 trước khi cho phép bước tiếp theo
- Ghi `metadata.json` với danh sách file backup thật, dung lượng và SHA-256
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
10. Chờ bản sau mở khóa bước ghi file game sau khi backup/restore đã được test an toàn.

## Verify APK

Sau khi tải APK, so sánh SHA-256 của APK với file `sha256.txt` trong GitHub Releases.

Không cài APK từ mirror lạ, link chat riêng, hoặc file không có SHA-256 đi kèm.

Ví dụ file phát hành hợp lệ:

```text
WUWA-VN-v2.3.0-release.apk
```

Không phát hành file `app-debug.apk` cho người dùng phổ thông.

Trước khi phát hành, kiểm tra chữ ký:

```bash
apksigner verify --print-certs WUWA-VN-v2.3.0-release.apk
```

## Cách Khôi Phục

Mở app, chọn **Restore Original Files**, chọn bản backup muốn dùng, rồi bấm **Restore**.

Từ `v2.3.0`, backup được lưu trong thư mục app-specific external storage để tránh xin quyền lưu trữ rộng:

```text
Android/data/com.acceleratorer.wuwavn/files/WUWA-VH-Backup/
  2026-05-15_22-30-10/
    Engine.ini
    DeviceProfiles.ini
    MountLang_en.txt
    metadata.json
```

Restore thật vẫn đang khóa. Bản `v2.4.0` sẽ show restore dry-run trước, sau đó mới tính tới restore write unlock.

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
- Bản `v2.3.0` đã backup read-only file cấu hình thật bằng Shizuku, nhưng chưa ghi file game thật

## Báo Lỗi

Khi gặp lỗi, hãy gửi kèm log trong app nếu có:

```text
[22:31:10] App version: 2.3.0
[22:31:10] Android version: 14
[22:31:11] Shizuku: running
[22:31:11] Permission: granted
[22:31:12] Game folder: found
[22:31:15] Backup read: copied Engine.ini
[22:31:16] Backup read: copied DeviceProfiles.ini
[22:31:16] Backup read: copied MountLang_en.txt
[22:31:16] Backup metadata: wrote actual backed-up files
[22:31:17] Patch download: success
[22:31:17] SHA-256: verified
[22:31:20] Apply patch: locked
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
