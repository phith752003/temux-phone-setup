#!/data/data/com.termux/files/usr/bin/bash
# Auto-start homelab services khi Android boot

# Giữ CPU hoạt động (không sleep)
termux-wake-lock

# Khởi động SSH server
sshd

# Đợi mạng Wi-Fi ổn định
sleep 10

# Khởi động API server
bash ~/homelab/scripts/start-api.sh

# Log thời gian boot
mkdir -p ~/homelab/logs
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Homelab auto-started after boot" >> ~/homelab/logs/boot.log
