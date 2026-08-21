# WUWA Việt Hoá Android

Ứng dụng cài bản Việt hoá cho **Wuthering Waves Global 3.6 trên Android**.

README này viết cho người mới hoàn toàn. Bạn chỉ cần làm theo đúng thứ tự bên dưới.

## Tóm tắt cực ngắn

```text
1. Cài Shizuku và bật Shizuku
2. Cài WUWA VN
3. Mở game một lần để game tải xong dữ liệu
4. Trong WUWA VN: Backup Game Configs
5. Trong WUWA VN: Download & Verify Patch
6. Trong WUWA VN: Install Vietnamese Patch
7. Mở Wuthering Waves và kiểm tra tiếng Việt
```

Nếu một nút đang màu xám hoặc bị khoá, đừng cố bấm. Đọc mục [Nút bị khoá nghĩa là gì?](#nut-bi-khoa-nghia-la-gi).

## Tải đúng file

### APK WUWA VN v3.6.0

- [Tải APK trực tiếp](https://github.com/Acceleratorer/WUWA-VN-Android/releases/download/v3.6.0/WUWA-VN-v3.6.0-release.apk)
- [Mở trang GitHub Releases](https://github.com/Acceleratorer/WUWA-VN-Android/releases/tag/v3.6.0)

Tên file đúng là:

```text
WUWA-VN-v3.6.0-release.apk
```

Không tải hoặc cài các file sau:

- `app-debug.apk`
- `Source code (zip)` hoặc `Source code (tar.gz)`
- APK từ link lạ, link rút gọn, mirror không rõ nguồn

### Shizuku

WUWA VN cần Shizuku để truy cập thư mục dữ liệu của game. Tải Shizuku từ nguồn chính thức:

- [Tải Shizuku trên GitHub chính thức](https://github.com/RikkaApps/Shizuku/releases/latest)
- [Hướng dẫn Shizuku chính thức](https://shizuku.rikka.app/guide/setup/)

Chỉ tải Shizuku từ GitHub của `RikkaApps` hoặc trang `shizuku.rikka.app`.

## Trước khi cài: kiểm tra 5 điều

- Điện thoại chạy **Android 11 trở lên**.
- Đã cài **Wuthering Waves Global**, không phải bản Trung Quốc.
- Game có package `com.kurogame.wutheringwaves.global`.
- Game đã cập nhật đến **3.6.x**.
- Máy còn đủ dung lượng trống để chứa APK, patch và bản backup.

> Không cần root. Bản này dùng Shizuku. Root write không được bật.

## Cài lần đầu — làm từng bước

### Bước 1: Cài WUWA VN

1. Tải `WUWA-VN-v3.6.0-release.apk` từ link GitHub ở trên.
2. Mở file APK.
3. Nếu Android hỏi quyền cài ứng dụng không rõ nguồn, bật **Allow from this source / Cho phép từ nguồn này** cho trình duyệt hoặc trình quản lý file đang dùng.
4. Bấm **Install / Cài đặt**.

Nếu Android báo đã có ứng dụng khác cùng tên hoặc cài đặt thất bại, gỡ **WUWA VN cũ** rồi cài lại APK v3.6.0. Không gỡ Wuthering Waves.

### Bước 2: Cài và bật Shizuku

Trên Android 11 trở lên, Shizuku thường được bật bằng **Wireless debugging**. Tên menu có thể hơi khác tuỳ hãng máy.

1. Mở **Settings / Cài đặt** của điện thoại.
2. Tìm **Developer options / Tùy chọn nhà phát triển**. Nếu chưa có, vào **About phone / Giới thiệu điện thoại**, bấm **Build number / Số hiệu bản dựng** khoảng 7 lần để mở.
3. Vào Developer options và bật **Wireless debugging / Gỡ lỗi không dây**.
4. Mở ứng dụng **Shizuku**.
5. Nếu Shizuku yêu cầu ghép đôi:
   - Trong Shizuku, chọn **Pairing**.
   - Trong Android, mở **Wireless debugging > Pair device with pairing code**.
   - Nhập mã ghép đôi vào thông báo hoặc ô nhập của Shizuku.
6. Quay lại Shizuku và bấm **Start / Bắt đầu** ở mục Wireless debugging.
7. Chờ Shizuku hiện trạng thái đang chạy.

Sau mỗi lần khởi động lại điện thoại, Android có thể yêu cầu bấm Start Shizuku lại. Đây là giới hạn của Wireless debugging, không phải lỗi WUWA VN.

Nếu không tìm thấy menu, mở [hướng dẫn Shizuku chính thức](https://shizuku.rikka.app/guide/setup/) và tìm phần **Start via wireless debugging**.

### Bước 3: Cho WUWA VN quyền Shizuku

1. Mở **WUWA VN**.
2. Bấm **Open Shizuku**.
3. Nếu Shizuku hiện yêu cầu cấp quyền cho WUWA VN, bấm **Allow / Cho phép**.
4. Quay lại WUWA VN.
5. Màn hình phải hiện Shizuku ở trạng thái **READY** hoặc **Sẵn sàng**.

Nếu chưa READY, xem [Sửa lỗi Shizuku](#1-shizuku-chua-ready).

### Bước 4: Mở game một lần

1. Mở Wuthering Waves Global.
2. Đợi game tải và giải nén xong toàn bộ dữ liệu bản 3.6.
3. Vào được màn hình chính của game rồi thoát game hoàn toàn.

> Không để game đang chạy khi backup hoặc cài patch.

### Bước 5: Backup trước

Trong WUWA VN:

1. Bấm **Backup Game Configs**.
2. Chờ đến khi app báo backup thành công.
3. Chỉ tiếp tục khi backup hiển thị đủ và **VERIFIED**.

Backup là bản sao an toàn để app có thể phục hồi trạng thái gốc khi gỡ patch. Đừng xoá dữ liệu WUWA VN sau khi backup nếu bạn còn đang dùng patch.

### Bước 6: Tải và kiểm tra patch

1. Bấm **Download & Verify Patch**.
2. Chờ tải xong. Patch khoảng **65.2 MB**.
3. Chờ app báo SHA-256 đã được kiểm tra thành công.

Nếu tải lỗi hoặc SHA-256 không khớp, **không cài patch**. Xem [Sửa lỗi tải patch](#3-tai-patch-loi-hoac-sha-256-khong-khop).

### Bước 7: Cài bản Việt hoá

1. Đóng Wuthering Waves hoàn toàn.
2. Trong WUWA VN, bấm **Install Vietnamese Patch**.
3. Đọc màn hình kế hoạch cài đặt.
4. Nếu mọi thứ đúng, bấm nút xác nhận cài.
5. Chờ app báo cài thành công và kiểm tra xong.
6. Mở Wuthering Waves Global 3.6 và kiểm tra menu, nhiệm vụ hoặc hội thoại.

App sẽ tự kiểm tra đường dẫn game. App chỉ ghi đúng các file patch cần thiết và có rollback nếu transaction gặp lỗi.

## Cập nhật patch khi đã cài bản cũ

Khi game lên bản 3.6.x mới hoặc có patch Việt hoá mới:

1. Cập nhật Wuthering Waves và mở game một lần để game tải xong dữ liệu.
2. Thoát game hoàn toàn.
3. Mở WUWA VN và bảo đảm Shizuku đang **READY**.
4. Bấm **Backup Game Configs** nếu app yêu cầu.
5. Bấm **Download & Verify Patch**.
6. Bấm **Install Vietnamese Patch** khi nút được bật.

Không tự xoá file cũ bằng trình quản lý file. Không chép PAK thủ công.

## Gỡ bản Việt hoá

Nếu muốn quay về bản gốc:

1. Đóng Wuthering Waves.
2. Mở WUWA VN và bảo đảm Shizuku đang **READY**.
3. Vào **More Tools > Remove Vietnamese Patch**.
4. Đọc kế hoạch phục hồi.
5. Chỉ xác nhận khi app báo trusted backup là **VERIFIED**.
6. Bấm **Remove Patch Now**.
7. Chờ app báo đã phục hồi và xoá patch thành công.

Không tự xoá `WuWaVH_99_P.pak`, `.sig` hoặc sửa `MountLang_en.txt` bằng tay. App cần phục hồi các file theo đúng thứ tự để game không bị trạng thái nửa cài nửa gỡ.

> Nếu nút Remove bị khoá, thường là vì chưa có trusted original backup đúng với dữ liệu game hiện tại. Đừng xoá file thủ công; gửi State Snapshot để kiểm tra.

## Nút bị khoá nghĩa là gì?

| Nút | Điều kiện để dùng |
|---|---|
| **Open Shizuku** | Luôn có thể mở Shizuku hoặc trang hướng dẫn |
| **Backup Game Configs** | Game Global được nhận diện và Shizuku READY |
| **Download & Verify Patch** | Có thể tải patch; cần mạng ổn định |
| **Install Vietnamese Patch** | Game 3.6.x, Shizuku READY, backup gốc VERIFIED, layout game hợp lệ và patch đã verify |
| **Remove Vietnamese Patch** | Có patch và trusted original backup VERIFIED |
| **Apply Safe / Balanced / Performance** | Đang bị khoá trong v3.6.0 để tránh ghi sai config |
| **Restore Original Files** | Restore write tổng quát đang bị khoá trong v3.6.0 |
| **Max Graphics** | Luôn bị khoá trong bản này |

Nút bị khoá là cơ chế an toàn. Đừng thử root, đừng sửa file bằng tay và đừng dùng app khác để “ép” cài.

## Các lỗi thường gặp

### 1. Shizuku chưa READY

- Mở Shizuku và kiểm tra service có đang chạy không.
- Bật lại Wireless debugging.
- Nếu điện thoại vừa restart, bấm Start Shizuku lại.
- Mở WUWA VN > **Open Shizuku** và cấp quyền cho WUWA VN.
- Nếu máy Xiaomi/POCO yêu cầu, bật thêm **USB debugging (Security settings)** trong Developer options.
- Cho phép Shizuku chạy nền, không bật chế độ tiết kiệm pin quá mạnh cho Shizuku.

### 2. WUWA VN không thấy game

- Kiểm tra bạn cài bản **Global**, không phải bản Trung Quốc.
- Mở game một lần, chờ tải xong resource rồi thoát hoàn toàn.
- Kiểm tra game là bản **3.6.x**.
- Vào WUWA VN > **More Tools > Check Game Folder**.
- Nếu vẫn không thấy, vào **More Tools > Game Path Diagnostic**, bấm copy report và gửi report.

### 3. Tải patch lỗi hoặc SHA-256 không khớp

- Kiểm tra mạng và thử lại sau.
- Không đổi URL patch trong app hoặc tải PAK từ nguồn khác.
- Xoá phiên tải lỗi trong app nếu app yêu cầu rồi tải lại.
- Nếu vẫn lỗi, gửi **Copy Debug Log**; không gửi file APK/PAK lên nhóm công khai nếu không cần.

### 4. Install bị khoá dù đã backup

- Backup phải được tạo **sau khi game đã lên 3.6.x**.
- Backup phải có đủ 3 mục và trạng thái **VERIFIED**.
- Shizuku phải hiện **READY**.
- Game phải đang đóng hoàn toàn.
- Bấm **More Tools > Game Path Diagnostic** để kiểm tra layout Resources 3.6.
- Gửi **Copy State Snapshot** và diagnostic report nếu vẫn không được.

### 5. Android không cho cài APK

- Vào Settings > Security/Privacy > Install unknown apps.
- Cho phép đúng ứng dụng đang mở APK: Chrome, Files, ZArchiver, v.v.
- Mở lại file `WUWA-VN-v3.6.0-release.apk`.
- Nếu báo xung đột, gỡ WUWA VN cũ rồi cài lại. Không gỡ Wuthering Waves.

### 6. Game bị lỗi sau khi cài

1. Không mở game liên tục để thử lại.
2. Đóng game.
3. Mở WUWA VN > **More Tools > Remove Vietnamese Patch**.
4. Chỉ xác nhận nếu app tìm thấy trusted backup VERIFIED.
5. Nếu Remove cũng bị khoá, gửi State Snapshot và Debug Log; không tự xoá file.

## Kiểm tra APK có phải bản thật không (tuỳ chọn)

Người dùng bình thường chỉ cần tải từ GitHub Release chính thức. Nếu muốn tự kiểm tra, SHA-256 của APK v3.6.0 là:

```text
2a4ca78adfdbd6eabe08b43167b311a1bd0098b986577b9faa5515db7e19c523
```

Bạn có thể tải file [sha256.txt](https://github.com/Acceleratorer/WUWA-VN-Android/releases/download/v3.6.0/sha256.txt) cạnh APK để đối chiếu.

Trên Windows PowerShell:

```powershell
Get-FileHash .\WUWA-VN-v3.6.0-release.apk -Algorithm SHA256
```

Trên Linux/macOS:

```bash
sha256sum WUWA-VN-v3.6.0-release.apk
```

## Gửi báo lỗi cho dễ xử lý

Trong WUWA VN, vào **More Tools** rồi lấy:

1. **Copy State Snapshot** — thông tin trạng thái ngắn gọn.
2. **Game Path Diagnostic** — thông tin đường dẫn game 3.6.
3. **Copy Debug Log** hoặc **Send Issue Report** — log thao tác.

Kèm thêm:

- Dòng máy.
- Phiên bản Android.
- Phiên bản Wuthering Waves.
- Bạn đang làm đến bước nào.
- Ảnh lỗi nếu có.

Mẫu báo lỗi:

```text
Máy: Samsung/ Xiaomi/ ..., Android ...
Game: Wuthering Waves Global 3.6.x
Shizuku: READY / chưa READY
Patch state: ORIGINAL / PATCHED / PARTIAL / UNKNOWN
Bước lỗi: Download / Backup / Install / Remove
Thông báo lỗi: ...
Đã gửi kèm: State Snapshot + Game Path Diagnostic + Debug Log
```

## Bản này có gì và có gì chưa mở?

### Đã mở trong v3.6.0

- Cài hoặc cập nhật patch Việt hoá cho WUWA Global 3.6.x.
- Kiểm tra kích thước và SHA-256 của patch trước khi cài.
- Kiểm tra PAK, SIG và registry game cùng nhau.
- Cài/gỡ theo transaction và có rollback khi thao tác lỗi.
- Backup read-only các file cần cho khôi phục.

### Chưa mở trong v3.6.0

- Safe / Default config write.
- Balanced config write.
- Performance config write.
- Restore Original Files write tổng quát.
- Root write.
- Max Graphics.

Các mục trên bị khoá có chủ ý vì chưa hoàn tất kiểm chứng format và rollback trên game 3.6. Không phải lỗi cài đặt.

## Thông tin kỹ thuật cho người muốn biết

Patch v3.6.0 sử dụng layout động của game:

```text
Saved/Resources/<resource-version>/Lang_en/<lang-version>/WuWaVH_99_P.pak
Saved/Resources/<resource-version>/Lang_en/<lang-version>/WuWaVH_99_P.sig
Saved/Resources/<resource-version>/Mount/MountLang_en.txt
```

Thông tin patch:

```text
Patch version: wuwa-3.6.0-vi-2026.08
PAK: WuWaVH_99_P.pak
PAK size: 65,216,325 bytes
PAK SHA-256: 850db0d3865f29fe4502fbbd4439593068246929b7a98324d5c63ecde7136e52
```

App không cho nhập đường dẫn tuỳ ý và không xin quyền `REQUEST_INSTALL_PACKAGES`. App không tự cài APK âm thầm.

## Dành cho developer

Build debug:

```bash
./gradlew :app:assembleDebug
```

Build release cần keystore riêng và các biến môi trường ký APK. Release chính thức được build, ký, kiểm tra và upload bằng GitHub Actions.

Các kiểm tra release:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
python tools/verify-release-apk.py WUWA-VN-v3.6.0-release.apk
```

## Nguồn và giấy phép

- Patch Việt hoá được app tải từ endpoint đã pin kích thước và SHA-256; không tải PAK từ link khác.
- Ứng dụng Android: [Acceleratorer/WUWA-VN-Android](https://github.com/Acceleratorer/WUWA-VN-Android)
- Shizuku: [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)
- Giấy phép: [MIT](LICENSE)

Ứng dụng không liên kết với Kuro Games. Hãy backup trước khi sử dụng. Không dùng app để gian lận, bypass anti-cheat hoặc can thiệp gameplay online.
