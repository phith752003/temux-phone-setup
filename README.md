# Android Homelab Mini - Termux Native Server

Dự án biến điện thoại Android cũ (Vsmart Live 4, Snapdragon 675, 6GB RAM, 64GB Storage) thành một server mini hoạt động 24/7 chạy native trên Termux. Đây là giải pháp homelab gọn nhẹ, không cần root, tiết kiệm điện và tích hợp đầy đủ các công cụ lập trình, giám sát và triển khai website từ xa.

---

## Các Tính Năng Chính

* **SSH Key-based Authentication:** Kết nối bảo mật không cần mật khẩu từ Windows qua mạng LAN hoặc internet.
* **Auto-start on Boot:** Tự động khởi chạy SSH daemon và các dịch vụ API ngay khi điện thoại khởi động lại (thông qua Termux:Boot).
* **Hardware Monitoring:** Giám sát thời gian thực về RAM, dung lượng lưu trữ, CPU load, tiến trình hoạt động và nhiệt độ của các cảm biến trên điện thoại.
* **Web Deployment Pipeline:** Triển khai nhanh các dự án web (Static HTML, Node.js Express, Python FastAPI) chạy ngầm thông qua tmux.
* **Secure Remote Access:** Truy cập không dây và expose website ra internet an toàn bằng cách tích hợp Tailscale VPN và Cloudflare Tunnel.
* **Automated Backups:** Script sao lưu dữ liệu, cơ sở dữ liệu SQLite và mã nguồn định kỳ, tích hợp dọn dẹp file cũ và đồng bộ lên GitHub.

---

## Cấu Trúc Thư Mục Dự Án

```text
~/homelab/
├── apis/               # Các ứng dụng API (FastAPI Dashboard)
├── websites/           # Thư mục chứa các website được triển khai
├── data/               # Cơ sở dữ liệu SQLite và dữ liệu của các dịch vụ
├── logs/               # File ghi log của hệ thống và các dịch vụ
├── scripts/            # Các script bash quản lý và giám sát server
│   ├── backup.sh       # Sao lưu dữ liệu homelab cục bộ
│   ├── rclone-backup.sh # Đồng bộ backup và dữ liệu camera lên Google Drive
│   ├── record-camera.sh # Ghi luồng RTSP từ camera Yoosee
│   ├── deploy-website.sh # Khởi tạo nhanh project web mới
│   ├── monitor.sh      # Màn hình giám sát tài nguyên thời gian thực
│   ├── restart-api.sh  # Khởi động lại FastAPI dashboard
│   ├── start-api.sh    # Bật FastAPI dashboard
│   ├── start-bot.sh    # Bật Telegram Bot điều khiển
│   ├── start-tunnel.sh # Mở Cloudflare Tunnel để expose port ra ngoài
│   ├── status.sh       # Kiểm tra nhanh trạng thái hệ thống
│   ├── stop-api.sh     # Tắt FastAPI dashboard
│   ├── stop-bot.sh     # Tắt Telegram Bot
│   └── stress.sh       # Kiểm tra độ ổn định và nhiệt độ dưới tải
└── boot/               # Tập lệnh khởi động cho Termux:Boot


```

---

## Hướng Dẫn Kết Nối và Sử Dụng

### 1. Cấu Hình Port Forwarding trên Windows (Chế Độ USB)
Khi thực hiện lập trình trực tiếp qua cáp USB, chạy script PowerShell trên Windows để chuyển tiếp các cổng cần thiết:
```powershell
./start-adb-forward.ps1
```

### 2. Kết Nối SSH vào Homelab
File cấu hình SSH client của bạn trên Windows (`~/.ssh/config`) đã được thiết lập với các alias sau:

* **Kết nối không dây qua Tailscale:**
  ```bash
  ssh homelab
  ```
* **Kết nối qua cáp USB (ADB Forwarding):**
  ```bash
  ssh homelab-usb
  ```

### 3. Triển Khai Website Mới
Để khởi tạo và triển khai nhanh một website trên homelab, chạy script sau trên Termux:
```bash
bash ~/homelab/scripts/deploy-website.sh
```
Sau đó làm theo hướng dẫn để lựa chọn loại dự án (Static HTML, Node.js, hoặc Python FastAPI).

### 4. Expose Website Ra Internet
Để lấy một đường dẫn HTTPS công khai cho website của bạn thông qua Cloudflare Quick Tunnel (ví dụ với cổng 8080):
```bash
bash ~/homelab/scripts/start-tunnel.sh 8080
```

---

## Giám Sát và Bảo Trì

* **Xem trạng thái hệ thống:** Chạy `bash ~/homelab/scripts/status.sh` để kiểm tra RAM, ổ đĩa, CPU load và nhiệt độ.
* **Giám sát thời gian thực:** Chạy `bash ~/homelab/scripts/monitor.sh` để bật giao diện monitor tài nguyên và trạng thái các dịch vụ.
* **Kiểm tra nhiệt độ dưới tải:** Chạy `bash ~/homelab/scripts/stress.sh 2 60` để kiểm tra sự ổn định của nguồn và quạt làm mát trong 60 giây với 2 cores.
* **Sao lưu dữ liệu cục bộ:** Sao lưu định kỳ bằng cách chạy `bash ~/homelab/scripts/backup.sh`.
* **Sao lưu & đồng bộ đám mây (Google Drive):** Sử dụng Rclone để tự động hóa sao lưu dữ liệu hệ thống và hình ảnh/video camera bằng cách chạy `bash ~/homelab/scripts/rclone-backup.sh`. Xem hướng dẫn thiết lập rclone chi tiết ở phần dưới.

---

## ☁️ Hướng Dẫn Tích Hợp Google Drive (5TB) để Backup & Lưu Trữ Camera

Hệ thống sử dụng **Rclone** (công cụ đồng bộ đám mây mạnh mẽ, gọn nhẹ) để đẩy các bản backup và đồng bộ hình ảnh/video camera lên Google Drive mà không cần quyền root.

### 1. Cài đặt Rclone trên Termux
Chạy lệnh sau trên Termux để cài đặt:
```bash
pkg install rclone -y
```

### 2. Cấu hình Tài khoản Google Drive (Không cần GUI)
Do Termux chạy chế độ dòng lệnh (headless), quá trình xác thực OAuth Google cần sự trợ giúp của máy tính Windows (hoặc máy có trình duyệt web):

1. **Trên Termux:** Chạy lệnh bắt đầu cấu hình:
   ```bash
   rclone config
   ```
   * Chọn `n` (New remote).
   * Đặt tên remote đầu tiên là `gdrive_backup` (dành cho sao lưu hệ thống) hoặc `gdrive_camera` (dành cho ảnh/video camera).
   * Nhập số tương ứng với **Google Drive** (thường là `18` hoặc tìm từ khoá `drive`).
   * Bỏ trống các mục `client_id` và `client_secret` (nhấn Enter).
   * Chọn quyền truy cập: `1` (Full access to all files).
   * Bỏ trống `root_folder_id` và `service_account_file`.
   * Khi hỏi `Edit advanced config?`, chọn `n` (No).
   * Khi hỏi `Use auto config?`, chọn `n` (No) - **Bắt buộc để xác thực headless**.
   * Rclone sẽ hiển thị một lệnh mẫu, ví dụ: `rclone authorize "drive" "xxxxxxxxxxxxxxx"`. Hãy copy lệnh này.

2. **Trên Windows PC (Host):**
   * Đảm bảo bạn đã cài đặt Rclone trên Windows (tải từ [rclone.org](https://rclone.org/downloads/)).
   * Mở CMD/PowerShell trên Windows và chạy lệnh vừa copy từ Termux:
     ```powershell
     rclone authorize "drive" "xxxxxxxxxxxxxxx"
     ```
   * Trình duyệt web sẽ tự động mở ra. Đăng nhập vào tài khoản Google Drive 5TB của bạn và cấp quyền truy cập.
   * Sau khi thành công, Windows terminal sẽ in ra một đoạn mã JSON chứa access token (bắt đầu bằng `{"access_token":...}`). Hãy copy toàn bộ đoạn mã JSON này.

3. **Quay lại Termux:**
   * Dán đoạn mã JSON copy từ Windows vào dòng nhắc `result>` và nhấn Enter.
   * Chọn tài khoản là **Shared Drive** (nếu tài khoản 5TB của bạn là Shared Drive/Team Drive) hoặc Regular Drive.
   * Xác nhận lưu cấu hình (`y`).

*Lặp lại các bước trên để cấu hình thêm các tài khoản lưu trữ phụ (ví dụ đặt tên là: `gdrive_cam1`, `gdrive_cam2`...).*

### 3. Cấu hình ổ đĩa ảo gộp nhiều tài khoản (Rclone Union)
Để gộp 2 hoặc nhiều tài khoản 5TB độc lập đã tạo ở trên (ví dụ: `gdrive_cam1` và `gdrive_cam2`) thành một phân vùng lưu trữ gộp duy nhất tên là `gdrive_camera` (dung lượng gộp lên tới 10TB+):

1. **Mở trình cấu hình Rclone:**
   ```bash
   rclone config
   ```
2. **Khởi tạo Remote ảo gộp:**
   * Chọn `n` (New remote).
   * Đặt tên remote ảo là **`gdrive_camera`** (đây là tên mặc định mà script camera và web dashboard sử dụng để lưu trữ và xem lại video).
   * Khi hỏi chọn loại lưu trữ, hãy nhập số tương ứng với **`union`** (hoặc gõ từ khóa `union`).
   * Tại dòng nhắc **`upstreams>`**, điền danh sách các tài khoản kèm thư mục lưu trữ, phân tách nhau bởi dấu cách:
     ```text
     gdrive_cam1:camera-backups gdrive_cam2:camera-backups
     ```
   * Tại dòng nhắc **`action_policy>`**, chọn **`mfs`** (Most free space - tự động lưu video mới vào tài khoản còn trống nhiều dung lượng nhất) hoặc chọn **`ff`** (First Found - ghi đầy tài khoản 1 rồi tự chuyển tiếp sang tài khoản 2).
   * Tại dòng nhắc **`create_policy>`**, chọn tương tự như trên (`mfs` hoặc `ff`).
   * Các câu hỏi nâng cao nâng khác bấm Enter để chọn mặc định.
   * Xác nhận lưu cấu hình (`y`).

Bây giờ bạn đã có một phân vùng ảo mang tên `gdrive_camera:` tập hợp toàn bộ dung lượng của các tài khoản Google Drive của bạn. Khi bạn xem lại camera, Rclone sẽ tự tìm file trên tất cả tài khoản để phát lại mà bạn không cần bận tâm file nằm ở đâu!


### 4. Đồng bộ hóa dữ liệu tự động
* **Cấp quyền truy cập bộ nhớ điện thoại (Camera) cho Termux:**
  ```bash
  termux-setup-storage
  ```
* **Chạy thử nghiệm đồng bộ:**
  ```bash
  bash ~/homelab/scripts/rclone-backup.sh
  ```
* **Kiểm tra log hoạt động:**
  ```bash
  cat ~/homelab/logs/rclone-backup.log
  ```

### 5. Tự động hóa qua Cronjob (Định kỳ hàng ngày)

Để hệ thống tự động backup và đồng bộ lên Google Drive mỗi ngày lúc 3:00 sáng:
1. Cài đặt cronie: `pkg install cronie -y`
2. Mở trình chỉnh sửa cronjob: `crontab -e`
3. Thêm dòng sau và lưu lại:
   ```text
   0 3 * * * /data/data/com.termux/files/usr/bin/bash /data/data/com.termux/files/home/homelab/scripts/rclone-backup.sh >/dev/null 2>&1
   ```

---


## 🗺️ Roadmap

Xem [ROADMAP.md](ROADMAP.md) để biết các ý tưởng và kế hoạch phát triển tiếp theo cho dự án, bao gồm:

* 🚀 Web hosting trên domain `phith.click`
* 🤖 Nâng cấp Telegram Bot với AI chatbot
* 🛠️ DevOps pipeline (CI/CD, Git server, reverse proxy)
* 📡 REST API & webhook services
* 📊 Dashboard giám sát & analytics
* 🎓 Learning lab & experimentation
