#!/data/data/com.termux/files/usr/bin/bash
# Dừng FastAPI server

stop_session() {
    local SESSION=$1
    if tmux has-session -t "$SESSION" 2>/dev/null; then
        tmux kill-session -t "$SESSION"
        echo "Session '$SESSION' đã được dừng thành công."
    else
        echo "Thông báo: Session '$SESSION' không hoạt động."
    fi
}

stop_session "homelab-private"
stop_session "homelab-public"
