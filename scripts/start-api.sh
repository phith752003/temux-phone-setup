#!/data/data/com.termux/files/usr/bin/bash
# Khởi động FastAPI trong tmux session

SESSION="test-api"
API_DIR="$HOME/homelab/apis/test-api"
PORT=3000
LOG="$HOME/homelab/logs/api.log"

# Kiểm tra session đã tồn tại chưa
if tmux has-session -t "$SESSION" 2>/dev/null; then
    echo "Cảnh báo: Session '$SESSION' đang hoạt động."
    echo "Sử dụng lệnh: tmux attach -t $SESSION để truy cập."
    exit 0
fi

# Kiểm tra port đã bị chiếm chưa
if ss -tlnp 2>/dev/null | grep -q ":$PORT "; then
    echo "Lỗi: Port $PORT đã bị chiếm bởi tiến trình khác!"
    ss -tlnp | grep ":$PORT "
    exit 1
fi

# Tạo thư mục log nếu chưa có
mkdir -p "$(dirname "$LOG")"

# Khởi động trong tmux
tmux new-session -d -s "$SESSION" -c "$API_DIR" \
    "uvicorn main:app --host 0.0.0.0 --port $PORT 2>&1 | tee -a $LOG"

sleep 2

# Kiểm tra thành công
if tmux has-session -t "$SESSION" 2>/dev/null; then
    echo "API server đã khởi động thành công trên port $PORT"
    echo "  Session: $SESSION"
    echo "  Đường dẫn Log: $LOG"
else
    echo "Lỗi: Khởi động thất bại. Chi tiết log bên dưới:"
    tail -20 "$LOG" 2>/dev/null
    exit 1
fi
