#!/data/data/com.termux/files/usr/bin/bash
# Script ghi luồng RTSP từ Camera Yoosee thành các phân đoạn 15 phút
# Cấu trúc lưu trữ: Nam_[Year]/Thang_[Month]/Ngay_[Day]/[Start_Time]_to_[End_Time].mp4
# Hỗ trợ ghi nhận vào SQLite DB và tự động sync lên Google Drive qua Rclone

# --- CẤU HÌNH CAMERA ---
CAMERA_IP="192.168.1.100"       # IP Camera Yoosee của bạn
CAMERA_PORT="554"               # Port mặc định
NVR_PASSWORD="your_password"    # Mật khẩu NVR thiết lập trong App Yoosee
STREAM_PATH="onvif1"            # onvif1 (HD) hoặc onvif2 (SD - Tiết kiệm dung lượng)

# --- CẤU HÌNH CLOUD & LOCAL ---
RCLONE_REMOTE="gdrive"          # Tên remote Rclone đã cấu hình
DB_PATH="$HOME/homelab/data/camera.db"
TEMP_DIR="$HOME/homelab/data/camera/temp"
LOG_FILE="$HOME/homelab/logs/camera-record.log"

mkdir -p "$TEMP_DIR"
mkdir -p "$HOME/homelab/logs"

# Khởi tạo Database SQLite nếu chưa có
sqlite3 "$DB_PATH" "CREATE TABLE IF NOT EXISTS recordings (id INTEGER PRIMARY KEY AUTOINCREMENT, filename TEXT NOT NULL, year INTEGER NOT NULL, month INTEGER NOT NULL, day INTEGER NOT NULL, start_time TEXT NOT NULL, end_time TEXT NOT NULL, remote_path TEXT NOT NULL, created_at TEXT NOT NULL);"

# RTSP URL
RTSP_URL="rtsp://admin:${NVR_PASSWORD}@${CAMERA_IP}:${CAMERA_PORT}/${STREAM_PATH}"

record_segment() {
    # 1. Tính toán mốc thời gian 15 phút (làm tròn khớp với mốc 00, 15, 30, 45)
    YEAR=$(date +%Y)
    MONTH=$(date +%m)
    DAY=$(date +%d)
    HOUR=$(date +%H)
    MIN=$(date +%M)

    # Làm tròn xuống mốc 15 phút trước đó
    START_MIN=$(( (10#$MIN / 15) * 15 ))
    END_MIN=$(( START_MIN + 15 ))
    END_HOUR=$HOUR

    if [ $END_MIN -eq 60 ]; then
        END_MIN=0
        END_HOUR=$(( (10#$HOUR + 1) % 24 ))
    fi

    # Định dạng chuỗi giờ phút
    START_TIME_STR=$(printf "%02d:%02d" $HOUR $START_MIN)
    END_TIME_STR=$(printf "%02d:%02d" $END_HOUR $END_MIN)
    
    FILE_NAME_STR=$(printf "%02d-%02d_to_%02d-%02d" $HOUR $START_MIN $END_HOUR $END_MIN)
    FILENAME="${FILE_NAME_STR}.mp4"
    
    # Thư mục lưu trữ trên Google Drive
    REMOTE_DIR="camera-backups/Nam_${YEAR}/Thang_${MONTH}/Ngay_${DAY}"
    REMOTE_PATH="${RCLONE_REMOTE}:${REMOTE_DIR}/${FILENAME}"

    # File tạm trên bộ nhớ cục bộ
    LOCAL_FILE="${TEMP_DIR}/${FILENAME}"

    echo "🎨 Đang chuẩn bị ghi phân đoạn: ${START_TIME_STR} -> ${END_TIME_STR}" | tee -a "$LOG_FILE"
    echo "📂 Điểm lưu trữ GDrive: ${REMOTE_DIR}/${FILENAME}" | tee -a "$LOG_FILE"

    # Ghi hình trong 900 giây (15 phút) bằng ffmpeg
    # Sử dụng '-rtsp_transport tcp' để luồng truyền tải ổn định không bị vỡ hình
    # '-c copy' để lưu trực tiếp luồng h264/h265 không decode, CPU load ~ 1%
    ffmpeg -y -rtsp_transport tcp -i "$RTSP_URL" \
           -c copy -t 900 \
           "$LOCAL_FILE" >> "$LOG_FILE" 2>&1

    # Kiểm tra xem file video có được tạo thành công không
    if [ -f "$LOCAL_FILE" ] && [ -s "$LOCAL_FILE" ]; then
        echo "✅ Ghi hình cục bộ thành công: ${FILENAME}" | tee -a "$LOG_FILE"
        
        # 2. Upload lên Google Drive qua Rclone
        echo "🚀 Đang upload lên Google Drive..." | tee -a "$LOG_FILE"
        rclone copy "$LOCAL_FILE" "${RCLONE_REMOTE}:${REMOTE_DIR}" >> "$LOG_FILE" 2>&1
        
        if [ $? -eq 0 ]; then
            echo "☁️ Đã sync lên Cloud thành công!" | tee -a "$LOG_FILE"
            
            # 3. Ghi nhận thông tin vào Database SQLite
            sqlite3 "$DB_PATH" "INSERT INTO recordings (filename, year, month, day, start_time, end_time, remote_path, created_at) VALUES ('${FILENAME}', ${YEAR}, ${MONTH}, ${DAY}, '${START_TIME_STR}', '${END_TIME_STR}', '${REMOTE_DIR}/${FILENAME}', datetime('now'));"
            
            # 4. Giữ lại file cục bộ làm Cache và dọn dẹp các file cũ hơn 12 tiếng (720 phút)
            echo "🧹 Đang dọn dẹp bộ nhớ đệm (cache) camera..." | tee -a "$LOG_FILE"
            find "$TEMP_DIR" -name "*.mp4" -mmin +720 -delete
            echo "💾 Đã lưu file làm cache cục bộ." | tee -a "$LOG_FILE"

        else
            echo "❌ Lỗi: Đồng bộ Rclone lên Google Drive thất bại!" | tee -a "$LOG_FILE"
        fi
    else
        echo "❌ Lỗi: ffmpeg không thể ghi hình hoặc camera bị ngắt kết nối!" | tee -a "$LOG_FILE"
        sleep 10 # Tránh lặp vô hạn quá nhanh nếu camera bị mất mạng
    fi
}

# Hỗ trợ 2 chế độ chạy:
# 1. Chế độ DAEMON (chạy liên tục trong tmux): Chạy script không có đối số
# 2. Chế độ CRON (chạy 1 phân đoạn rồi nghỉ): Chạy script kèm tham số "once" (ví dụ: bash record-camera.sh once)

if [ "$1" = "once" ]; then
    record_segment
else
    echo "🚀 Bắt đầu ghi hình Camera Yoosee ở chế độ chạy ngầm (Daemon Mode)..." | tee -a "$LOG_FILE"
    while true; do
        record_segment
    done
fi
