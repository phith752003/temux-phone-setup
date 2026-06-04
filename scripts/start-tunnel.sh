#!/data/data/com.termux/files/usr/bin/bash
# start-tunnel.sh - Khởi chạy Cloudflare Quick Tunnel cho website

PORT=${1:-3000}
SESSION="cf-tunnel-$PORT"
LOG_FILE="$HOME/homelab/logs/tunnel-$PORT.log"

mkdir -p "$HOME/homelab/logs"

# Dừng tunnel cũ nếu đang chạy trên port này
if tmux has-session -t "$SESSION" 2>/dev/null; then
    echo "Đang dừng tunnel cũ trên session '$SESSION'..."
    tmux kill-session -t "$SESSION"
    sleep 1
fi

echo "Đang khởi chạy Cloudflare Tunnel cho cổng $PORT..."
tmux new-session -d -s "$SESSION" "cloudflared tunnel --url http://localhost:$PORT 2>&1 | tee $LOG_FILE"

echo "Đang lấy URL công khai từ Cloudflare..."
for i in {1..12}; do
    sleep 1.5
    if [ -f "$LOG_FILE" ]; then
        URL=$(grep -o 'https://[a-zA-Z0-9-]\+\.trycloudflare\.com' "$LOG_FILE" | head -n1)
        if [ -n "$URL" ]; then
            echo ""
            echo "=========================================================="
            echo "WEBSITE CỦA BẠN ĐÃ ĐƯỢC PUBLIC RA INTERNET!"
            echo "Đường dẫn công khai:  $URL"
            echo "Cổng nội bộ:          http://localhost:$PORT"
            echo "Session Tmux:          $SESSION"
            echo "=========================================================="
            exit 0
        fi
    fi
done

echo "Lỗi: Không thể lấy URL công khai từ Cloudflare. Chi tiết log:"
tail -n 20 "$LOG_FILE"
