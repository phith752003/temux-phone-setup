#!/data/data/com.termux/files/usr/bin/bash
# Dừng FastAPI server

SESSION="test-api"

if tmux has-session -t "$SESSION" 2>/dev/null; then
    tmux kill-session -t "$SESSION"
    echo "Session '$SESSION' đã được dừng thành công."
else
    echo "Thông báo: Session '$SESSION' không hoạt động."
fi
