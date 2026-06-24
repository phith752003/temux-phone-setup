---
name: deploy-camera-sync
description: >-
  Cài đặt, đồng bộ, cấu hình rclone và chạy thử nghiệm sao lưu hệ thống Camera Sync trên điện thoại Termux.
---

# Triển Khai Camera Sync & GDrive Union Cho Homelab Termux

## Overview
Skill này đóng gói quy trình đồng bộ mã nguồn, cấu hình các tài khoản Google Drive, thiết lập ổ gộp union storage duy nhất (`gdrive:`), phân quyền thiết bị Android, cài đặt thư viện và kiểm tra hoạt động của hệ thống đồng bộ camera và backup homelab trên điện thoại Vsmart Live 4 (chạy Termux).

## Dependencies
Không có.

## Quick Start

Để đồng bộ mã nguồn từ Windows sang điện thoại:
```powershell
powershell -ExecutionPolicy Bypass -File .\sync-to-phone.ps1
```

Để chạy setup thư viện & phân quyền trên Termux:
```bash
ssh phone "bash ~/temux-install/setup.sh"
```

Khởi động lại FastAPI API:
```bash
ssh phone "bash ~/homelab/scripts/restart-api.sh"
```

## Workflow

### 1. Đồng Bộ Mã Nguồn Từ Windows Lên Điện Thoại
*   Chạy script PowerShell `sync-to-phone.ps1` ở thư mục gốc của project để nén và đồng bộ mã nguồn qua SFTP lên thư mục `~/temux-install` trên điện thoại.

### 2. Cài Đặt Thư Viện Và Phân Quyền
*   Chạy lệnh `ssh phone "bash ~/temux-install/setup.sh"`.
*   Script này sẽ cài đặt các gói hệ thống, tải bánh xe python cho `pydantic-core` tương thích với Android ARM64, cài đặt `fastapi`, `uvicorn`, `python-multipart` và sao chép các tệp mã nguồn vào đúng cấu trúc thư mục của `~/homelab`.

### 3. Cấu Hình Google Drive và GDrive Union
*   Khi muốn thêm tài khoản Google Drive mới (`gdrive_camX`):
    1. Chạy lệnh sau trên PowerShell Windows để lấy token đăng nhập:
       ```powershell
       $Url = "https://downloads.rclone.org/v1.66.0/rclone-v1.66.0-windows-amd64.zip"; Invoke-WebRequest -Uri $Url -OutFile "rclone.zip"; Expand-Archive -Path "rclone.zip" -DestinationPath "." -Force; .\rclone-v1.66.0-windows-amd64\rclone.exe authorize "drive" "eyJzY29wZSI6ImRyaXZlIn0"
       ```
    2. Sử dụng script python hoặc lệnh rclone trên điện thoại để cập nhật tệp cấu hình `~/.config/rclone/rclone.conf`.
    3. Cấu hình ổ ảo gộp duy nhất **`gdrive`** dưới dạng `union` trỏ tới tất cả các upstream GDrive con (ví dụ: `gdrive_cam1: gdrive_cam2:`) sử dụng thuật toán Most Free Space (`mfs`) để tự động tối ưu hóa và gộp không gian lưu trữ của các tài khoản.
    4. Lúc này, mọi đường dẫn sao lưu sẽ dùng chung cấu trúc:
       - `gdrive:camera-backups` cho hình ảnh/video camera.
       - `gdrive:homelab-backups` cho sao lưu hệ thống.

### 4. Cấp Quyền Truy Cập Bộ Nhớ Cho Termux (Android 11+)
*   Mở cài đặt điện thoại: **Cài đặt -> Ứng dụng -> Termux -> Quyền -> Bộ nhớ -> Chọn "Cho phép quản lý tất cả các tệp"** (All files access).
*   Chạy `termux-setup-storage` trên Termux để làm mới các liên kết thư mục của bộ nhớ dùng chung (`~/storage/shared`).
*   Restart lại tiến trình Termux (gõ `exit` rồi mở lại, chạy lại `sshd`) để cập nhật quyền.

### 5. Kiểm Tra Chạy Thử Nghiệm Sao Lưu
*   Chạy lệnh sao lưu để đồng bộ dữ liệu hệ thống và hình ảnh camera lên đám mây:
    ```bash
    ssh phone "bash ~/homelab/scripts/rclone-backup.sh"
    ```
*   Đọc log tại `~/homelab/logs/rclone-backup.log` để kiểm tra kết quả đồng bộ.

## Common Mistakes
*   **Permission Denied với thư mục /sdcard**: Xảy ra do chưa cấp quyền "Quản lý tất cả các tệp" cho Termux trong Cài đặt Android, hoặc chưa restart lại tiến trình Termux sau khi cấp quyền.
*   **Lỗi Union Upstream**: Rclone Union sẽ crash nếu chỉ có duy nhất 1 upstream được định nghĩa. Nếu chỉ có 1 tài khoản Drive, hãy cấu hình remote dưới dạng `alias`, khi có từ 2 tài khoản trở lên mới chuyển cấu hình sang `union`.
