# Hướng dẫn đồng bộ code từ Windows lên Termux qua SFTP

Tài liệu này hướng dẫn cách cấu hình và sử dụng công cụ đồng bộ tự động **`sync-to-phone.ps1`** để chuyển đổi mã nguồn và triển khai các thay đổi từ máy tính Windows của bạn lên điện thoại Android (Termux) một cách nhanh chóng qua giao thức SFTP.

---

## 💡 Cơ chế hoạt động của Script Đồng Bộ
Khi bạn lập trình trên Windows, các trình soạn thảo thường sử dụng ký tự xuống dòng định dạng **CRLF (`\r\n`)**. Tuy nhiên, môi trường Termux (Linux) yêu cầu định dạng **LF (`\n`)**. Nếu đẩy trực tiếp tệp tin CRLF lên điện thoại, các kịch bản bash sẽ bị lỗi `\r: command not found` hoặc treo tiến trình.

Script `sync-to-phone.ps1` tự động giải quyết vấn đề này bằng cách:
1. **Quét toàn bộ dự án** trong máy tính của bạn.
2. **Chuyển đổi tạm thời** tất cả các ký tự xuống dòng từ **CRLF sang LF** trong bộ nhớ đệm trước khi gửi đi.
3. **Truyền tệp tin qua SFTP** bảo mật và lưu vào thư mục staging `~/temux-install/` trên điện thoại.
4. **Bỏ qua các tệp tin không cần thiết** (như `node_modules`, `.venv`, `.git`, `.vscode`, `config.json`) giúp tốc độ truyền tải cực kỳ nhanh.

---

## 🛠️ Yêu Cầu Cài Đặt (Prerequisites)

### 1. Cấu hình SSH Client trên Windows
Bạn cần tạo cấu hình kết nối nhanh bằng cách thêm thông tin sau vào tệp tin SSH config của bạn trên Windows (thường ở đường dẫn `C:\Users\<Tên_User>\.ssh\config`):

```text
Host phone
    HostName <IP_Tailscale_Hoặc_IP_Wifi_Của_Điện_Thoại>
    User u0_a472
    Port 8022
    IdentityFile ~/.ssh/id_rsa
```
*(Thay thế `HostName` bằng IP thực tế của điện thoại, ví dụ IP Tailscale `100.90.102.11`)*

**Yêu cầu:** Bạn có thể kết nối thành công bằng lệnh `ssh phone` trong terminal mà không cần nhập mật khẩu (sử dụng SSH Key).

### 2. Kích hoạt SSH Server trên Termux
Đảm bảo máy chủ SSH trên điện thoại đang chạy:
```bash
sshd
```

---

## 🚀 Hướng Dẫn Sử Dụng

Mỗi khi bạn thực hiện sửa đổi mã nguồn hoặc kịch bản ở máy tính Windows và muốn đẩy chúng lên điện thoại, hãy mở PowerShell trong thư mục dự án và chạy:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\sync-to-phone.ps1
```

### 📝 Các thư mục và tệp tin được bỏ qua (Exclusions)
Để tránh làm nặng bộ nhớ điện thoại và bảo mật thông tin cá nhân, các mục sau đây sẽ **không** được đồng bộ:
- `.git/` (Lịch sử git)
- `.vscode/` (Cấu hình riêng của VS Code)
- `node_modules/` (Thư viện Node.js, nên cài trực tiếp trên điện thoại)
- `venv/` & `.venv/` (Thư viện ảo Python)
- `config.json` (Tránh ghi đè thông tin cấu hình Token Telegram cá nhân trên điện thoại)

---

## 🔄 Áp Dụng Thay Đổi Trên Điện Thoại (Deployment)

Tệp tin sau khi chạy script đồng bộ sẽ nằm ở thư mục staging `~/temux-install/`. Để đưa chúng vào sử dụng thực tế (thư mục chạy chính thức `~/homelab/`), hãy chạy lệnh sau từ SSH của điện thoại:

```bash
# Đồng bộ các tệp tin script và bots vào homelab
cp -r ~/temux-install/bots/* ~/homelab/bots/
cp -r ~/temux-install/scripts/* ~/homelab/scripts/

# Cấp quyền thực thi cho các file script
chmod +x ~/homelab/scripts/*.sh
```

Nếu bạn thay đổi cấu hình Telegram Bot, đừng quên khởi động lại bot để áp dụng code mới:
```bash
bash ~/homelab/scripts/stop-bot.sh && bash ~/homelab/scripts/start-bot.sh
```

---

## 🔍 Giải Quyết Sự Cố (Troubleshooting)
- **Lỗi `Host phone not found`**: Kiểm tra lại tệp tin `~/.ssh/config` trên Windows và đảm bảo bạn đã bật Tailscale VPN (hoặc kết nối cùng mạng Wifi).
- **Lỗi `Connection refused`**: Đảm bảo bạn đã chạy lệnh `sshd` trong Termux trên điện thoại.
