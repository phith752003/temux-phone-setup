#!/data/data/com.termux/files/usr/bin/bash
# Script dừng hoạt động của Telegram Bot homelab

SESSION="telegram-bot"

if tmux has-session -t "$SESSION" 2>/dev/null; then
    tmux kill-session -t "$SESSION"
    echo "Đã dừng hoạt động của Telegram Bot (Đã kill session tmux '$SESSION')."
else
    echo "Telegram Bot đang không chạy (Không tìm thấy session tmux '$SESSION')."
fi
