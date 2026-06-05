# 🗺️ Roadmap - Android Homelab Mini

Tổng hợp các ý tưởng phát triển cho dự án homelab chạy trên điện thoại Android (Vsmart Live 4).  
Mỗi mục được đánh dấu trạng thái triển khai, ưu tiên theo độ khả thi và giá trị thực tế.

> **Trạng thái:** `[ ]` Chưa làm · `[/]` Đang làm · `[x]` Hoàn thành

---

## 🚀 Phase 1 — Web Hosting & Domain (`phith.click`)

Domain đã kết nối qua Cloudflare (SSL/TLS, proxy, CDN). Ưu tiên cao nhất vì hạ tầng đã sẵn sàng.

- [ ] **Personal Portfolio / Blog**
  - Host website cá nhân lên `phith.click`
  - Stack đề xuất: Hugo / Jekyll (static site generator) hoặc viết tay HTML/CSS/JS
  - Serve qua Nginx hoặc Caddy trong Ubuntu PRoot
  - Cloudflare Tunnel để expose port ra internet

- [ ] **Link Shortener**
  - Service rút gọn link riêng kiểu `phith.click/abc`
  - Backend: Node.js/Express hoặc Python/FastAPI + SQLite
  - Giao diện quản lý đơn giản

- [ ] **Pastebin Cá Nhân**
  - Nơi lưu và chia sẻ code snippets, ghi chú nhanh
  - Hỗ trợ syntax highlighting, expiration time
  - API endpoint để paste từ terminal: `curl -d "code here" phith.click/paste`

- [ ] **File Sharing Server**
  - Upload/download file qua giao diện web
  - Hỗ trợ drag-and-drop, hiển thị preview
  - Giới hạn dung lượng và xác thực cơ bản

---

## 🤖 Phase 2 — Nâng Cấp Telegram Bot

Bot Telegram hiện tại đã hoạt động với chức năng điều khiển server từ xa. Mở rộng thêm các tính năng AI và tự động hóa.

- [ ] **AI Chatbot qua Telegram**
  - Kết nối `opencode` CLI hoặc Gemini API vào Telegram bot
  - Gửi tin nhắn → bot trả lời bằng AI
  - Hỗ trợ context/conversation memory

- [ ] **Server Health Monitor**
  - Bot tự động gửi alert khi:
    - CPU > 80%, RAM > 90%
    - Disk usage > 85%
    - Nhiệt độ vượt ngưỡng an toàn
    - Service bị crash
  - Báo cáo tổng hợp hàng ngày lúc 8h sáng

- [ ] **Scheduled Tasks (Cron Jobs)**
  - Quản lý cron jobs qua Telegram
  - Thêm/xóa/liệt kê scheduled tasks
  - Gửi kết quả chạy cron qua bot

- [ ] **Web Scraper Bot**
  - Đặt lịch crawl data từ các website
  - Gửi kết quả qua Telegram theo định kỳ
  - Use case: theo dõi giá sản phẩm, tin tức, thời tiết

---

## 🛠️ Phase 3 — DevOps & Infrastructure

Biến homelab thành môi trường phát triển và triển khai hoàn chỉnh.

- [ ] **Self-hosted Git Server**
  - Cài đặt Gitea (lightweight Git hosting)
  - Hoặc sử dụng bare Git repos + git hooks
  - Truy cập qua `git.phith.click`

- [ ] **CI/CD Pipeline**
  - Auto-deploy khi push code lên Git
  - Git hook → build → restart service
  - Notification qua Telegram bot khi deploy xong

- [ ] **Reverse Proxy (Nginx/Caddy)**
  - Serve nhiều service trên cùng domain
  - `phith.click` → portfolio
  - `api.phith.click` → REST API
  - `git.phith.click` → Gitea
  - SSL termination tại Cloudflare

- [ ] **VPN / Proxy Cá Nhân**
  - WireGuard VPN server
  - Hoặc SOCKS5 proxy
  - Truy cập mạng nội bộ từ xa an toàn

---

## 📡 Phase 4 — API & Services

Xây dựng các API service chạy trên server.

- [ ] **REST API Server**
  - Framework: FastAPI (Python) hoặc Express (Node.js)
  - Endpoints phục vụ app mobile hoặc frontend
  - Swagger/OpenAPI documentation tự động

- [ ] **Webhook Receiver**
  - Nhận webhook từ GitHub, Cloudflare, các dịch vụ bên ngoài
  - Trigger actions tự động (deploy, notify, log)

- [ ] **Status Page**
  - Trang hiển thị trạng thái các service đang chạy
  - Uptime monitoring
  - Public tại `status.phith.click`

---

## 📊 Phase 5 — Monitoring & Analytics

Nâng cao khả năng giám sát và phân tích.

- [ ] **Dashboard Web**
  - Giao diện web hiển thị metrics real-time
  - CPU, RAM, disk, network, temperature
  - Biểu đồ lịch sử theo thời gian

- [ ] **Log Aggregation**
  - Thu thập logs từ tất cả services
  - Tìm kiếm và filter logs qua web UI
  - Alert khi phát hiện error patterns

- [ ] **Uptime Tracking**
  - Ping check các service định kỳ
  - Tính toán uptime percentage
  - Báo cáo hàng tuần

---

## 🎓 Phase 6 — Learning & Experimentation

Sử dụng server làm môi trường học tập và thử nghiệm.

- [ ] **Docker-like Environment**
  - Tận dụng PRoot để tạo nhiều môi trường isolated
  - Thử nghiệm các distro Linux khác nhau

- [ ] **Database Playground**
  - SQLite, PostgreSQL (trong PRoot)
  - Học và thực hành SQL queries
  - Backup tự động

- [ ] **Networking Lab**
  - Thực hành cấu hình mạng
  - DNS, firewall rules, port forwarding
  - Tailscale mesh networking

---

## 📋 Thứ Tự Ưu Tiên Đề Xuất

| # | Dự án | Lý do ưu tiên | Độ khó |
|---|-------|---------------|--------|
| 1 | Portfolio trên `phith.click` | Domain + Cloudflare đã sẵn sàng | ⭐ |
| 2 | AI Chatbot qua Telegram | opencode đã cài, bot đã chạy | ⭐⭐ |
| 3 | Reverse Proxy (Nginx) | Nền tảng cho tất cả service khác | ⭐⭐ |
| 4 | Server Health Monitor | Bảo vệ server, cảnh báo sớm | ⭐⭐ |
| 5 | Link Shortener | Dự án nhỏ, showcase domain | ⭐ |
| 6 | CI/CD Pipeline | Tự động hóa deploy | ⭐⭐⭐ |
| 7 | Status Page | Public monitoring | ⭐⭐ |
| 8 | Self-hosted Git | Độc lập khỏi GitHub | ⭐⭐⭐ |

---

## 🔧 Tech Stack Tổng Hợp

| Layer | Công nghệ |
|-------|-----------|
| **OS** | Android 11 → Termux → Ubuntu PRoot |
| **Web Server** | Nginx / Caddy |
| **Backend** | Node.js, Python (FastAPI) |
| **Database** | SQLite |
| **Tunnel** | Cloudflare Tunnel / Tailscale |
| **Domain** | `phith.click` (Cloudflare DNS) |
| **Bot** | Telegram Bot API (node-telegram-bot-api) |
| **AI** | opencode CLI / Gemini API |
| **Version Control** | Git + GitHub |
| **Process Manager** | tmux / pm2 |

---

> 💡 **Ghi chú:** Roadmap này sẽ được cập nhật khi hoàn thành các mục tiêu. Đánh dấu `[x]` khi hoàn thành, `[/]` khi đang triển khai.
