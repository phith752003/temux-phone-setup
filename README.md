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
│   ├── backup.sh       # Sao lưu dữ liệu homelab
│   ├── deploy-web.sh   # Khởi tạo nhanh project web mới
│   ├── monitor.sh      # Màn hình giám sát tài nguyên thời gian thực
│   ├── restart-api.sh  # Khởi động lại FastAPI dashboard
│   ├── start-api.sh    # Bật FastAPI dashboard
│   ├── start-tunnel.sh # Mở Cloudflare Tunnel để expose port ra ngoài
│   ├── status.sh       # Kiểm tra nhanh trạng thái hệ thống
│   ├── stop-api.sh     # Tắt FastAPI dashboard
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
* **Sao lưu dữ liệu:** Sao lưu định kỳ bằng cách chạy `bash ~/homelab/scripts/backup.sh`.

---

## 🗺️ Roadmap

Xem [ROADMAP.md](ROADMAP.md) để biết các ý tưởng và kế hoạch phát triển tiếp theo cho dự án, bao gồm:

* 🚀 Web hosting trên domain `phith.click`
* 🤖 Nâng cấp Telegram Bot với AI chatbot
* 🛠️ DevOps pipeline (CI/CD, Git server, reverse proxy)
* 📡 REST API & webhook services
* 📊 Dashboard giám sát & analytics
* 🎓 Learning lab & experimentation
