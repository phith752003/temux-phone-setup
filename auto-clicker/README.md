# 🎯 Auto Clicker - Android Macro Automation

App Android Auto Clicker sử dụng Accessibility Service để thực hiện gesture tự động (tap/swipe/wait). Thiết kế tối ưu pin/nhiệt, không yêu cầu root.

## ⚠️ Disclaimer

App này dùng cho:
- UI testing cá nhân
- Thao tác lặp lại hợp lệ
- Macro automation cá nhân

**KHÔNG** dùng cho: gian lận game, farm reward, bypass security, hoặc tương tác tự động với app bên thứ ba.

---

## 📋 Yêu cầu

- Windows PC với Java JDK 17+
- Android device với USB Debugging đã bật
- ADB (`C:\platform-tools\adb.exe`)
- Android SDK (cài qua script tự động)

## 🚀 Setup lần đầu

### 1. Cài Android SDK + Gradle (chỉ cần chạy 1 lần)

```powershell
.\scripts\setup-android-sdk.ps1
```

Script sẽ tự động:
- Tải Android Command-Line Tools
- Cài build-tools, platforms (SDK 34)
- Tải Gradle 8.7
- Tạo Gradle wrapper trong project
- Set environment variables

### 2. Restart PowerShell

Sau khi chạy setup, đóng và mở lại PowerShell để load PATH mới.

### 3. Build APK

```powershell
.\scripts\build-debug.ps1
```

### 4. Kết nối điện thoại

```powershell
.\scripts\adb-devices.ps1
```

### 5. Install APK

```powershell
.\scripts\install-debug.ps1
```

### 6. Mở app

```powershell
.\scripts\open-app.ps1
```

---

## 📱 Hướng dẫn sử dụng

### Bật Accessibility Service

1. Mở app → Tab **Home**
2. Nhấn **"Open Accessibility Settings"**
3. Tìm **"Auto Clicker"** trong danh sách
4. Bật service → Nhấn **OK** xác nhận

### Bật Overlay Permission

1. Tab **Home** → Nhấn **"Grant Overlay Permission"**
2. Bật **"Allow display over other apps"**

### Bật Overlay panel

1. Tab **Home** → Nhấn **"Show Overlay"**
2. Panel nổi sẽ xuất hiện trên màn hình
3. Kéo thanh header để di chuyển

### Chạy macro

1. Overlay panel → Nhấn **▶ (Start)**
2. Macro chạy theo profile đã chọn
3. **⏸** để pause, **⏹** để stop
4. **⚠ EMERGENCY STOP** xuất hiện khi đang chạy

### Tạo/Sửa Profile
 
1. Tab **Profiles** → **Create Profile**
2. Đặt tên, loop count, delay
3. Tab **Editor** → thêm actions (Tap/Swipe/Wait)
 
### Trỏ Điểm Trực Quan (Floating Pointers)
 
1. Khi bật Overlay panel ở tab Home.
2. Sang tab Editor, thêm hành động Tap hoặc Swipe.
3. Trên màn hình sẽ xuất hiện các bong bóng trỏ điểm màu đỏ (ví dụ: `1`, `S2`, `E2`...).
4. Bạn có thể kéo thả trực tiếp các bong bóng này tới bất kỳ vị trí nào trên màn hình. Tọa độ của hành động sẽ tự động cập nhật thời gian thực!
5. Khi bắt đầu chạy macro, các bong bóng này sẽ tự ẩn đi để không cản trở thao tác.
 
### Nhận Diện Hình Ảnh & Điều Kiện Rẽ Nhánh (Decision Tree)
 
1. Tại tab Home, nhấn **"Request Screen Capture"** để cấp quyền chụp màn hình (MediaProjection).
2. Vào tab Editor, nhấn vào một Action trong danh sách để mở hộp thoại cấu hình nâng cao.
3. Cài đặt điều kiện:
   - **Condition Type:** Chọn `IMAGE_FOUND` (Nếu thấy hình ảnh) hoặc `IMAGE_NOT_FOUND`.
   - **Similarity Threshold:** Độ khớp yêu cầu (mặc định 0.8).
   - **Search Region:** Giới hạn vùng quét theo tỷ lệ % `[Left, Top, Right, Bottom]` để thuật toán chạy cực nhanh (<5ms).
   - **Branching Jumps:** 
     - **True Index:** Chỉ số hành động tiếp theo (1-indexed) nếu điều kiện thỏa mãn (Nhập `-1` để click trực tiếp vào tọa độ tâm của hình ảnh được nhận diện).
     - **False Index:** Chỉ số hành động tiếp theo (1-indexed) nếu điều kiện sai (Nhập `-1` để bỏ qua hành động).
4. Nhấn **"Set Demo 5x5 Red Button Template"** để tạo nhanh ảnh mẫu pixel đỏ dùng cho việc test.

---

## 🔧 Scripts ADB

| Script | Chức năng |
|--------|-----------|
| `scripts\adb-devices.ps1` | Liệt kê device ADB |
| `scripts\build-debug.ps1` | Build debug APK |
| `scripts\install-debug.ps1` | Install APK lên device |
| `scripts\open-app.ps1` | Mở app trên device |
| `scripts\logcat-app.ps1` | Xem log filtered theo app |
| `scripts\reinstall-debug.ps1` | Uninstall + reinstall |

## 📊 Xem Logcat

```powershell
.\scripts\logcat-app.ps1
```

Log tags:
- `AutoClickerApp` - General app
- `MacroRunner` - Macro execution
- `GestureService` - Accessibility gestures
- `OverlayService` - Floating panel
- `BatteryMonitor` - Battery tracking
- `ThermalMonitor` - Temperature tracking
- `ScreenMonitor` - Screen on/off
- `ForegroundSvc` - Notification service

---

## 🔋 Tối ưu Pin/Nhiệt

### Thiết kế tiết kiệm pin:
- ❌ Không OCR, không quay video màn hình liên tục.
- ⚡ Screen capture & Image recognition chỉ chạy **khi có hành động yêu cầu** (on-demand), quét trong vùng giới hạn (Bounding Box) bằng thuật toán so khớp điểm khóa **Keypoint-guided SAD** siêu nhẹ trong <5ms.
- ❌ Không polling liên tục gây lag.
- ❌ Không spam gesture không có delay.
- ❌ Không wake lock.
- ✅ Foreground service chỉ chạy khi macro RUNNING.
- ✅ Overlay chỉ update khi state thay đổi.
- ✅ Metrics update tối đa 1 lần/giây.

### Bảo vệ tự động:
- Pin < 20% (không sạc) → **Auto pause**
- Thermal SEVERE/CRITICAL → **Auto pause**
- Màn hình tắt → **Auto pause** (phải bấm Resume)
- Max run time → **Auto stop** (mặc định 30 phút)
- Rest period → Nghỉ tự động (mặc định 30s mỗi 10 phút)

---

## ⚙️ Kiến trúc

```
auto-clicker/app/src/main/java/com/autoclicker/app/
├── MainActivity.kt                    # Entry point
├── service/
│   ├── AutoClickerService.kt          # Accessibility + dispatchGesture
│   ├── OverlayService.kt              # Floating control panel
│   ├── MacroForegroundService.kt      # Notification service
│   └── StopActionReceiver.kt          # Notification stop button handler
├── macro/
│   ├── MacroProfile.kt                # Profile data model
│   ├── MacroAction.kt                 # Action data model (TAP/SWIPE/WAIT)
│   ├── MacroState.kt                  # State enum
│   └── MacroRunner.kt                 # Coroutine-based executor
├── storage/
│   └── ProfileStorage.kt              # JSON file storage
├── monitor/
│   ├── BatteryMonitor.kt              # Battery level tracking
│   ├── ThermalMonitor.kt              # Device temperature
│   └── ScreenStateMonitor.kt          # Screen on/off
└── ui/
    ├── home/HomeFragment.kt           # Permission status
    ├── profile/ProfileFragment.kt     # Profile list
    ├── editor/MacroEditorFragment.kt  # Action editor
    └── status/StatusFragment.kt       # Runtime status
```

---

## ❌ Giới hạn

- Tọa độ tap/swipe là tuyệt đối (pixel), cần điều chỉnh theo độ phân giải màn hình
- Không có chế độ "pick point on screen" (cần nhập tọa độ thủ công)
- Không hỗ trợ gesture phức tạp (multi-touch, pinch)
- Không có scheduling (chạy macro theo lịch)
- Export/Import qua copy-paste JSON hoặc file text (chưa có file picker UI)

## 🔨 Troubleshooting

| Vấn đề | Giải pháp |
|--------|-----------|
| App crash khi mở | Kiểm tra `.\scripts\logcat-app.ps1` |
| Accessibility không hoạt động | Vào Settings → Accessibility → bật lại service |
| Overlay không hiện | Kiểm tra quyền "Display over other apps" |
| Gesture không hoạt động | Kiểm tra Accessibility Service đã bật |
| Build lỗi | Chạy lại `.\scripts\setup-android-sdk.ps1` |
| Device not found | Kiểm tra USB cable, bật USB Debugging |

---

## 📄 License

Personal use only. Not for distribution or commercial use.
