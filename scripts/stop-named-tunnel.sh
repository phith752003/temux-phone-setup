#!/data/data/com.termux/files/usr/bin/bash
# stop-named-tunnel.sh - Dừng Cloudflare Named Tunnel

SESSION="cf-named-tunnel"

if tmux has-session -t "$SESSION" 2>/dev/null; then
    tmux kill-session -t "$SESSION"
    echo "Đã dừng Cloudflare Named Tunnel (Đã kill session tmux '$SESSION')."
else
    echo "Cloudflare Named Tunnel đang không chạy (Không tìm thấy session tmux '$SESSION')."
fi
