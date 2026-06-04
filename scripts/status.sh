#!/data/data/com.termux/files/usr/bin/bash
# Kiểm tra trạng thái homelab

echo "========================================="
echo "       BÁO CÁO TRẠNG THÁI HOMELAB        "
echo "========================================="
echo ""

# Uptime
echo "Thời gian hoạt động (Uptime):"
uptime 2>/dev/null || echo "  N/A"
echo ""

# RAM
echo "Bộ nhớ RAM:"
free -h 2>/dev/null || (echo "  $(grep MemTotal /proc/meminfo)" && echo "  $(grep MemAvailable /proc/meminfo)")
echo ""

# Storage
echo "Dung lượng lưu trữ (Storage):"
df -h ~ | awk 'NR==2 {printf "  Tổng: %s  Đã dùng: %s (%s)  Còn trống: %s\n", $2, $3, $5, $4}'
echo ""

# CPU Load
echo "Tải lượng CPU:"
cat /proc/loadavg 2>/dev/null | awk '{printf "  1 phút: %s  5 phút: %s  15 phút: %s\n", $1, $2, $3}'
echo ""

# tmux sessions
echo "Các session tmux đang chạy:"
tmux ls 2>/dev/null || echo "  Không có session nào hoạt động"
echo ""

# Port 3000
echo "Trạng thái API (cổng 3000):"
if timeout 1 bash -c '</dev/tcp/127.0.0.1/3000' 2>/dev/null; then
    echo "  ĐANG HOẠT ĐỘNG"
    curl -s http://127.0.0.1:3000/health 2>/dev/null || echo "  (Không thể kết nối endpoint health)"
else
    echo "  KHÔNG HOẠT ĐỘNG"
fi
echo ""

# SSH
echo "Trạng thái SSH (cổng 8022):"
if pgrep sshd > /dev/null 2>&1; then
    echo "  sshd đang chạy"
else
    echo "  sshd KHÔNG chạy"
fi
echo ""

# Temperature
echo "Nhiệt độ thiết bị:"
found_temp=0
for zone in /sys/class/thermal/thermal_zone*/temp; do
    if [ -r "$zone" ]; then
        temp=$(cat "$zone" 2>/dev/null)
        if [ -n "$temp" ] && [ "$temp" -gt 0 ] 2>/dev/null; then
            zone_dir=$(dirname "$zone")
            name=$(cat "$zone_dir/type" 2>/dev/null || basename "$zone_dir")
            echo "  $name: $((temp/1000))°C"
            found_temp=1
        fi
    fi
done
if [ "$found_temp" -eq 0 ]; then
    echo "  Không thể đọc nhiệt độ cảm biến"
fi

# Battery via termux-api
if command -v termux-battery-status &> /dev/null; then
    echo ""
    echo "Thông tin Pin:"
    termux-battery-status 2>/dev/null | grep -E '"temperature"|"percentage"|"status"|"plugged"' | sed 's/[",]//g; s/^/  /'
fi
echo ""
echo "========================================="
