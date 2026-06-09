#!/data/data/com.termux/files/usr/bin/bash
# Khởi động các dịch vụ FastAPI trong tmux session

# Hàm hỗ trợ khởi động một API service
start_api() {
    local SESSION=$1
    local API_DIR=$2
    local PORT=$3
    local LOG=$4

    echo "Khởi động service $SESSION trên port $PORT..."

    # Kiểm tra session đã tồn tại chưa
    if tmux has-session -t "$SESSION" 2>/dev/null; then
        echo " -> Cảnh báo: Session '$SESSION' đang hoạt động."
        echo " -> Sử dụng lệnh: tmux attach -t $SESSION để truy cập."
        return 0
    fi

    # Kiểm tra port đã bị chiếm chưa
    if ss -tlnp 2>/dev/null | grep -q ":$PORT "; then
        echo " -> Lỗi: Port $PORT đã bị chiếm bởi tiến trình khác!"
        return 1
    fi

    # Tạo thư mục log nếu chưa có
    mkdir -p "$(dirname "$LOG")"

    # Khởi động trong tmux
    tmux new-session -d -s "$SESSION" -c "$API_DIR" \
        "uvicorn main:app --host 0.0.0.0 --port $PORT 2>&1 | tee -a $LOG"

    sleep 2

    # Kiểm tra thành công
    if tmux has-session -t "$SESSION" 2>/dev/null; then
        echo " -> [OK] Đã khởi động $SESSION trên port $PORT"
    else
        echo " -> [LỖI] Khởi động thất bại. Kiểm tra log: $LOG"
    fi
}

echo "=== STARTING ALL HOMELAB APIs ==="

# 1. Homelab Private API (Dashboard)
start_api "homelab-private" "$HOME/homelab/apis/test-api" 3000 "$HOME/homelab/logs/api_private.log"

# 2. Public API (URL Shortener, SaaS endpoints)
start_api "homelab-public" "$HOME/homelab/apis/public-api" 3001 "$HOME/homelab/logs/api_public.log"

echo "=== HOÀN TẤT ==="
