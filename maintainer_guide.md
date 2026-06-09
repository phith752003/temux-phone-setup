# Hướng Dẫn Vận Hành & Bảo Trì Hệ Thống Homelab Startup Combo

Tài liệu này ghi lại toàn bộ cấu trúc hệ thống, kiến trúc định tuyến và các thay đổi đã thực hiện để phục vụ cho việc bảo trì, nâng cấp bởi các lập trình viên hoặc AI khác.

---

## 1. Sơ Đồ Kiến Trúc Hệ Thống (Architecture Diagram)

Hệ thống được thiết kế theo mô hình **Defense in Depth** (Phòng thủ chiều sâu) để bảo vệ tài nguyên private của Homelab trong khi vẫn cung cấp các dịch vụ công khai ra Internet.

```mermaid
graph TD
    User([Người dùng ngoài Internet]) -->|HTTPS| CF[Cloudflare Edge]
    CF -->|Tunnel| CFT[Cloudflare Tunnel Client]
    CFT -->|Port 8080| Nginx[Nginx Reverse Proxy]
    
    subgraph Termux Environment (Vsmart Live 4)
        Nginx -->|phith.click| Site1[Web cá nhân: phideptrai/public]
        Nginx -->|tools.phith.click| Site2[PhiTools Hub: phitools/public]
        Nginx -->|compare.phith.click| Site3[SEO Compare: compare/public]
        
        Nginx -->|s.phith.click| Site4[URL Shortener Frontend: url-shortener/public]
        Nginx -->|s.phith.click/api/| PublicAPI[Public API: Cổng 3001]
        
        Nginx -->|homelab.phith.click & api.phith.click| PrivateAPI[Private Portal: Cổng 3000]
    end
```

---

## 2. Cấu Trúc Thư Mục (Directory Structure)

Thư mục chính chạy trên Termux (`~/homelab`):
```
~/homelab/
├── apis/
│   ├── test-api/       # Homelab Control Portal (Private API - Port 3000)
│   └── public-api/     # URL Shortener Backend (Public API - Port 3001)
├── websites/
│   ├── phideptrai/     # Portfolio cá nhân chính (phith.click)
│   ├── phitools/       # Công cụ tiện ích (tools.phith.click)
│   ├── compare/        # Các trang so sánh tĩnh (compare.phith.click)
│   └── url-shortener/  # Giao diện rút gọn link (s.phith.click)
├── scripts/
│   ├── start-api.sh           # Script chạy cả 2 uvicorn API vào tmux
│   ├── start-named-tunnel.sh  # Script khởi động Cloudflare Tunnel trỏ vào Nginx (8080)
│   ├── status.sh              # Script kiểm tra sức khỏe hệ thống
│   └── seo-generator/         # Script python + Jinja2 tạo web tĩnh so sánh
└── nginx.conf          # File cấu hình Nginx chính của hệ thống
```

---

## 3. Chi Tiết Định Tuyến Nginx (`nginx.conf`)

Nginx lắng nghe trên cổng `8080` của Termux và phân phối traffic dựa trên header `Host`:
1.  **`phith.click` & `www.phith.click`**: Trả về trang tĩnh portfolio được khôi phục từ code cũ (`websites/phideptrai/public`).
2.  **`homelab.phith.click` & `api.phith.click`**: Chuyển hướng (reverse proxy) vào cổng `3000` phục vụ Homelab Dashboard.
3.  **`tools.phith.click`**: Trả về thư mục tĩnh của PhiTools (`websites/phitools/public`).
4.  **`compare.phith.click`**: Trả về thư mục tĩnh so sánh (`websites/compare/public`).
5.  **`s.phith.click`**:
    *   Truy cập `/`: Trả về giao diện Shortener tĩnh.
    *   Truy cập `/api/`: Chuyển tiếp vào Public API (Cổng `3001`).
    *   Truy cập `/[shortcode]`: Chuyển tiếp vào Public API để xử lý redirect và đếm click.

---

## 4. Quản Lý Cloudflare Tunnel & DNS

*   **Tài khoản & Xác thực:** Đã được cấu hình thông qua API token lưu tại file `/root/.cloudflared/cert.pem` của container Ubuntu.
*   **Lệnh đăng ký DNS Subdomain:**
    Để đăng ký thêm subdomain trỏ về tunnel, chạy lệnh sau trong container Ubuntu:
    ```bash
    cloudflared tunnel route dns vsmart-homelab <subdomain.domain.com>
    ```
*   **Lệnh chạy Tunnel:**
    Tunnel được duy trì bởi Tmux session `cf-named-tunnel` và chạy với tham số định tuyến tới cổng 8080:
    ```bash
    cloudflared tunnel run --url http://localhost:8080 --token <TOKEN>
    ```

---

## 5. Hướng Dẫn Triển Khai (Deployment Flow)

Khi thực hiện thay đổi code ở máy tính cá nhân (Windows):
1.  Chạy script PowerShell để đồng bộ code lên điện thoại qua SFTP:
    ```powershell
    .\sync-to-phone.ps1
    ```
    *Lưu ý:* Script sẽ tự động convert line-endings từ CRLF sang LF để tương thích với Linux/Android.
2.  SSH vào điện thoại (`ssh homelab`) và thực hiện sao chép file cấu hình + restart service:
    *   **Nginx:** Copy `~/temux-install/nginx.conf` vào `/data/data/com.termux/files/usr/etc/nginx/nginx.conf` và reload:
        ```bash
        nginx -s reload
        ```
    *   **Website:** Chạy script setup để cập nhật code website hoặc copy thủ công vào `~/homelab/websites/`.
    *   **Tunnels:** Khởi động lại tunnel nếu có thay đổi port:
        ```bash
        bash ~/homelab/scripts/stop-named-tunnel.sh
        bash ~/homelab/scripts/start-named-tunnel.sh
        ```

---

## 6. Kiểm Thử Hệ Thống (Verification Guide)

*   **Kiểm tra Port lắng nghe:**
    ```bash
    # Trong Termux
    pgrep -lf uvicorn
    pgrep -lf nginx
    ```
*   **Kiểm tra API Rút gọn Link:**
    ```bash
    # Tạo link rút gọn
    curl -X POST -H "Content-Type: application/json" -d '{"long_url": "https://google.com"}' https://s.phith.click/api/shorten
    
    # Kiểm tra chuyển hướng
    curl -I https://s.phith.click/<code_vừa_tạo>
    ```
*   **Kiểm tra API Hệ thống & Cảm biến:**
    ```bash
    # Kiểm tra JSON trả về từ cổng ngoài
    curl https://homelab.phith.click/system
    ```
