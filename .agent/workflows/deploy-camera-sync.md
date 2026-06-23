---
description: Hướng dẫn Agent triển khai Rclone, Google Drive Union và Camera Replay cho Homelab
---

Tập tài liệu này hướng dẫn chi tiết cho Agent cách tự động triển khai và hỗ trợ người dùng cấu hình hệ thống đồng bộ Google Drive 5TB và Camera Yoosee Replay.

## Quy Trình Triển Khai Từng Bước (Step-by-Step Deployment)

### Bước 1: Đồng bộ mã nguồn từ Windows lên điện thoại
Agent cần chạy script PowerShell cục bộ để đồng bộ toàn bộ file mới lên máy Termux:
// turbo
1. Chạy lệnh sau trên PowerShell Windows:
```powershell
powershell -ExecutionPolicy Bypass -File .\sync-to-phone.ps1
```

---

### Bước 2: Cài đặt thư viện và cấp quyền trên Termux
SSH vào điện thoại để chạy cài đặt các gói python mới và phân quyền cho các script vừa upload:
// turbo
2. Thực thi lệnh cập nhật qua SSH:
```bash
ssh phone "bash ~/homelab/setup.sh"
```
*(Nếu host `phone` không kết nối được, hãy thử với `ssh homelab` hoặc `ssh homelab-usb` dựa trên SSH config của người dùng).*

---

### Bước 3: Hướng dẫn người dùng cấu hình tài khoản Google Drive & Union
Bước này cần sự phối hợp của người dùng để đăng nhập Google qua trình duyệt PC:

3. Hướng dẫn người dùng chạy lệnh sau trên Termux để cấu hình từng tài khoản Google Drive độc lập (ví dụ đặt tên là `gdrive_cam1`, `gdrive_cam2`...):
```bash
rclone config
```
*Hãy nhắc nhở Agent giải thích chi tiết cách dùng lệnh `rclone authorize` trên Windows để lấy token và dán vào Termux (xem chi tiết mục 2 trong README.md).*

4. Sau khi người dùng cấu hình xong các tài khoản con, hướng dẫn họ tạo ổ ảo gộp mang tên **`gdrive_camera`** với loại remote là **`union`** và đường dẫn upstreams trỏ tới các ổ con:
```text
gdrive_cam1:camera-backups gdrive_cam2:camera-backups
```
Chính sách ghi/tải chọn: `mfs` (Most free space).

---

### Bước 4: Khởi động lại dịch vụ API để áp dụng giao diện mới
Khởi động lại các dịch vụ FastAPI trong tmux để nạp code giao diện Upload và Replay mới:
// turbo
5. Chạy lệnh restart API qua SSH:
```bash
ssh phone "bash ~/homelab/scripts/restart-api.sh"
```

---

### Bước 5: Cấu hình Cronjob tự động chạy hàng ngày
Thiết lập cronjob trên Termux để hệ thống tự động backup và đồng bộ lên Google Drive mỗi ngày lúc 3:00 sáng.

6. Kiểm tra xem cronie đã được cài đặt chưa:
```bash
ssh phone "pkg install cronie -y"
```

7. Thêm dòng cronjob sau vào crontab của Termux:
```text
0 3 * * * /data/data/com.termux/files/usr/bin/bash /data/data/com.termux/files/home/homelab/scripts/rclone-backup.sh >/dev/null 2>&1
```
*(Agent có thể chạy lệnh: `ssh phone "echo '0 3 * * * /data/data/com.termux/files/usr/bin/bash /data/data/com.termux/files/home/homelab/scripts/rclone-backup.sh >/dev/null 2>&1' | crontab -"` để ghi trực tiếp vào crontab).*
