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

# Khởi chạy bot natively trên Termux thông qua tmux
tmux new-session -d -s "$SESSION" "cd $BOT_DIR && node telegram-bot.js 2>&1 | tee -a $LOG"

sleep 2

if tmux has-session -t "$SESSION" 2>/dev/null; then
    echo "Telegram Bot đã khởi động trong session tmux: '$SESSION' (chạy ngầm trên Termux)"
    echo "Ghi log tại: $LOG"
else
    echo "Khởi động thất bại. Hãy kiểm tra log tại: $LOG"
fi
