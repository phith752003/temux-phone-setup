#!/data/data/com.termux/files/usr/bin/bash
# start-named-tunnel.sh - Khởi chạy Cloudflare Named Tunnel định danh

SESSION="cf-named-tunnel"
TUNNEL_DIR="$HOME/homelab/tunnels"
TOKEN_FILE="$TUNNEL_DIR/vsmart-homelab.token"
LOG_FILE="$HOME/homelab/logs/named-tunnel.log"

mkdir -p "$TUNNEL_DIR"
mkdir -p "$HOME/homelab/logs"

# Nếu truyền token qua đối số, lưu lại vào file
if [ -n "$1" ]; then
    echo "$1" > "$TOKEN_FILE"
    echo "Đã lưu token vào $TOKEN_FILE"
fi

# Kiểm tra xem có file token không
if [ ! -f "$TOKEN_FILE" ]; then
    echo "Lỗi: Không tìm thấy file token tại $TOKEN_FILE"
    echo "Sử dụng: bash start-named-tunnel.sh <YOUR_CLOUDFLARE_TOKEN>"
    exit 1
fi

TOKEN=$(cat "$TOKEN_FILE" | tr -d '\r\n[:space:]')

if [ -z "$TOKEN" ]; then
    echo "Lỗi: File token trống rỗng!"
    exit 1
fi

# Dừng tunnel cũ nếu đang chạy
if tmux has-session -t "$SESSION" 2>/dev/null; then
    echo "Đang dừng Named Tunnel cũ đang chạy..."
    tmux kill-session -t "$SESSION"
    sleep 1
fi

echo "Đang khởi chạy Cloudflare Named Tunnel..."
tmux new-session -d -s "$SESSION" "cloudflared tunnel run --url http://localhost:8080 --token $TOKEN 2>&1 | tee $LOG_FILE"

echo "Đang kiểm tra trạng thái khởi động..."
sleep 3

if tmux has-session -t "$SESSION" 2>/dev/null; then
    echo "Named Tunnel đã khởi động trong session tmux: $SESSION"
    echo "Chi tiết log tại: $LOG_FILE"
    echo "Nội dung log 5 dòng cuối:"
    tail -n 5 "$LOG_FILE"
else
    echo "Lỗi: Khởi chạy Named Tunnel thất bại!"
    tail -n 10 "$LOG_FILE"
    exit 1
fi
