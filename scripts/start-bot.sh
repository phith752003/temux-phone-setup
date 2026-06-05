#!/data/data/com.termux/files/usr/bin/bash
# Script khởi chạy Telegram Bot điều khiển homelab chạy ngầm trong tmux (Ubuntu proot)

SESSION="telegram-bot"
BOT_DIR="$HOME/homelab/bots"
LOG="$HOME/homelab/logs/bot.log"

if tmux has-session -t "$SESSION" 2>/dev/null; then
    echo "Session tmux '$SESSION' đang hoạt động."
    exit 0
fi

mkdir -p "$(dirname "$LOG")"

# Khởi chạy bot inside Ubuntu proot thông qua tmux
tmux new-session -d -s "$SESSION" "proot-distro login ubuntu -- bash -c 'cd /data/data/com.termux/files/home/homelab/bots && node telegram-bot.js 2>&1 | tee -a /data/data/com.termux/files/home/homelab/logs/bot.log'"

sleep 2

if tmux has-session -t "$SESSION" 2>/dev/null; then
    echo "Telegram Bot đã khởi động trong session tmux: '$SESSION' (chạy ngầm trong Ubuntu)"
    echo "Ghi log tại: $LOG"
else
    echo "Khởi động thất bại. Hãy kiểm tra log tại: $LOG"
fi
