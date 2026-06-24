#!/data/data/com.termux/files/usr/bin/bash
# Script sao lưu dữ liệu Homelab và đồng bộ ảnh/video Camera lên Google Drive qua Rclone

# --- CẤU HÌNH ĐƯỜNG DẪN ---
HOMELAB_DIR="$HOME/homelab"
BACKUP_DIR="$HOME/homelab-backups"
CAMERA_DIR="/sdcard/DCIM/Camera" # Thư mục chứa ảnh/video của camera trên Android
LOG_FILE="$HOMELAB_DIR/logs/rclone-backup.log"

# Cấu hình Rclone remotes (Bạn cần tạo cấu hình rclone trước với tên tương ứng)
RCLONE_BACKUP_REMOTE="gdrive:homelab-backups"
RCLONE_CAMERA_REMOTE="gdrive:camera-backups"

mkdir -p "$HOMELAB_DIR/logs"

# --- TÍCH HỢP TELEGRAM ALERTS ---
CONFIG_FILE="$HOMELAB_DIR/bots/config.json"
TELEGRAM_TOKEN=""
TELEGRAM_CHAT_ID=""

if [ -f "$CONFIG_FILE" ]; then
    # Parse nhanh token và chat_id từ config.json mà không cần cài jq
    TELEGRAM_TOKEN=$(grep -o '"token": *"[^"]*"' "$CONFIG_FILE" | cut -d'"' -f4)
    TELEGRAM_CHAT_ID=$(grep -o '"chat_id": *"[^"]*"' "$CONFIG_FILE" | cut -d'"' -f4)
fi

send_telegram_notification() {
    local message="$1"
    if [ -n "$TELEGRAM_TOKEN" ] && [ -n "$TELEGRAM_CHAT_ID" ]; then
        curl -s -X POST "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage" \
            -d "chat_id=$TELEGRAM_CHAT_ID" \
            -d "text=$message" \
            -d "parse_mode=Markdown" > /dev/null
    fi
}

echo "=== KHỞI ĐỘNG ĐỒNG BỘ GOOGLE DRIVE ($(date)) ===" | tee -a "$LOG_FILE"

# --- PHẦN 1: SAO LƯU DỮ LIỆU HỆ THỐNG (HOMELAB BACKUP) ---
echo "1. Đang chạy backup cục bộ..." | tee -a "$LOG_FILE"
# Khởi chạy script backup.sh gốc để tạo file tar.gz cục bộ mới nhất
if [ -f "$HOMELAB_DIR/scripts/backup.sh" ]; then
    bash "$HOMELAB_DIR/scripts/backup.sh" >> "$LOG_FILE" 2>&1
fi

echo "2. Đang đồng bộ các bản backup cục bộ lên Google Drive ($RCLONE_BACKUP_REMOTE)..." | tee -a "$LOG_FILE"
# Sử dụng rclone copy để đẩy file nén lên Google Drive
rclone copy "$BACKUP_DIR" "$RCLONE_BACKUP_REMOTE" --update --verbose >> "$LOG_FILE" 2>&1

if [ $? -eq 0 ]; then
    MSG_BACKUP="✅ *Homelab Backup:* Đã đồng bộ thành công các bản sao lưu hệ thống lên Google Drive!"
    echo "$MSG_BACKUP" | tee -a "$LOG_FILE"
    send_telegram_notification "$MSG_BACKUP"
else
    MSG_BACKUP="⚠️ *Homelab Backup:* Đồng bộ bản sao lưu hệ thống lên Google Drive THẤT BẠI. Kiểm tra log tại: $LOG_FILE"
    echo "$MSG_BACKUP" | tee -a "$LOG_FILE"
    send_telegram_notification "$MSG_BACKUP"
fi

# --- PHẦN 2: ĐỒNG BỘ ẢNH & VIDEO CAMERA ---
echo "3. Đang kiểm tra thư mục Camera..." | tee -a "$LOG_FILE"
# Kiểm tra xem Termux đã được cấp quyền truy cập bộ nhớ máy và thư mục Camera có tồn tại không
if [ -d "$CAMERA_DIR" ]; then
    echo "Phát hiện thư mục camera: $CAMERA_DIR. Bắt đầu đồng bộ lên ($RCLONE_CAMERA_REMOTE)..." | tee -a "$LOG_FILE"
    
    # Dùng rclone copy hoặc sync. 
    # - "copy" chỉ sao chép file mới lên Drive (kể cả khi bạn xóa file ở máy thì trên Drive vẫn còn). Phù hợp làm kho lưu trữ lâu dài.
    # - "sync" sẽ đồng bộ hoàn toàn (nếu xóa ở máy thì trên Drive cũng bị xóa theo).
    # Khuyến nghị dùng "copy" để bảo vệ dữ liệu camera tránh vô tình xóa trên điện thoại.
    rclone copy "$CAMERA_DIR" "$RCLONE_CAMERA_REMOTE" \
        --update \
        --transfers 4 \
        --checkers 8 \
        --verbose >> "$LOG_FILE" 2>&1
        
    if [ $? -eq 0 ]; then
        MSG_CAM="📸 *Camera Backup:* Đồng bộ hình ảnh và video thành công lên Google Drive!"
        echo "$MSG_CAM" | tee -a "$LOG_FILE"
        send_telegram_notification "$MSG_CAM"
    else
        MSG_CAM="⚠️ *Camera Backup:* Đồng bộ hình ảnh/video camera THẤT BẠI. Vui lòng kiểm tra log: $LOG_FILE"
        echo "$MSG_CAM" | tee -a "$LOG_FILE"
        send_telegram_notification "$MSG_CAM"
    fi
else
    echo "Không tìm thấy thư mục Camera tại: $CAMERA_DIR. Bỏ qua phần đồng bộ camera." | tee -a "$LOG_FILE"
    echo "Lưu ý: Chạy lệnh 'termux-setup-storage' trên điện thoại để cấp quyền truy cập bộ nhớ cho Termux." | tee -a "$LOG_FILE"
fi

echo "=== HOÀN THÀNH ĐỒNG BỘ GOOGLE DRIVE ($(date)) ===" | tee -a "$LOG_FILE"
echo "--------------------------------------------------" >> "$LOG_FILE"
