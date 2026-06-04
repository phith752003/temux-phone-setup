#!/data/data/com.termux/files/usr/bin/bash
# Khởi động lại API server sạch sẽ

echo "Đang khởi động lại API server..."
bash ~/homelab/scripts/stop-api.sh
sleep 2
bash ~/homelab/scripts/start-api.sh
