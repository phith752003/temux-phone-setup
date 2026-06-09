#!/data/data/com.termux/files/usr/bin/bash
# Auto-start homelab services khi Android boot
# Chạy tự động bởi Termux:Boot sau khi điện thoại khởi động

# Giữ CPU hoạt động (không sleep)
termux-wake-lock

# Khởi động SSH server
sshd

# Đợi mạng Wi-Fi ổn định (30 giây để Android kết nối Wi-Fi + Tailscale VPN)
sleep 30

# Kết nối Tailscale (fallback phòng khi Android chưa tự kết nối)
# Tailscale sẽ tự bỏ qua nếu đã kết nối rồi
if command -v tailscale &>/dev/null; then
    tailscale up 2>/dev/null &
fi

# Khởi động API server
bash ~/homelab/scripts/start-api.sh

# Khởi động Cloudflare Named Tunnel nếu có token
TOKEN_FILE="$HOME/homelab/tunnels/vsmart-homelab.token"
if [ -f "$TOKEN_FILE" ]; then
    bash ~/homelab/scripts/start-named-tunnel.sh
fi

# Log thời gian boot
mkdir -p ~/homelab/logs
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Homelab auto-started after boot" >> ~/homelab/logs/boot.log
