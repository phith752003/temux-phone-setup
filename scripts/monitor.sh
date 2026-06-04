#!/data/data/com.termux/files/usr/bin/bash
# Màn hình giám sát tài nguyên hệ thống

clear
echo "=========================================="
echo "   GIÁM SÁT HOMELAB - $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="
echo ""

# Uptime
echo "Thời gian hoạt động (Uptime):"
echo "  $(uptime 2>/dev/null || echo 'N/A')"
echo ""

# RAM
echo "Bộ nhớ RAM:"
free -h 2>/dev/null || (grep -E "MemTotal|MemFree|MemAvailable" /proc/meminfo | sed 's/^/  /')
echo ""

# Storage
echo "Dung lượng lưu trữ (Disk):"
df -h ~ | awk 'NR==2 {printf "  Tổng: %s  Đã dùng: %s (%s)  Còn trống: %s\n", $2, $3, $5, $4}'
echo ""

# CPU load
echo "Tải lượng CPU:"
cat /proc/loadavg 2>/dev/null | awk '{printf "  1 phút: %s  5 phút: %s  15 phút: %s\n", $1, $2, $3}'
echo ""

# Top processes
echo "Top 5 tiến trình tiêu thụ CPU nhiều nhất:"
ps aux 2>/dev/null | sort -k3 -rn | head -5 | awk '{printf "  PID:%-6s CPU:%-6s %s\n", $2, $3, $11}' 2>/dev/null || echo "  N/A"
echo ""

# Services
echo "Trạng thái Dịch vụ:"
printf "  SSH (8022): "
pgrep sshd > /dev/null 2>&1 && echo "Đang hoạt động" || echo "Đã dừng"

printf "  API (3000): "
if timeout 1 bash -c '</dev/tcp/127.0.0.1/3000' 2>/dev/null; then
    resp=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:3000/health 2>/dev/null)
    echo "Đang hoạt động (HTTP $resp)"
else
    echo "Đã dừng"
fi

printf "  tmux:       "
sessions=$(tmux ls 2>/dev/null | wc -l)
if [ "$sessions" -gt 0 ]; then
    echo "Đang hoạt động ($sessions session(s))"
    tmux ls 2>/dev/null | sed 's/^/              /'
else
    echo "Không có session hoạt động"
fi
echo ""

# Temperature
echo "Nhiệt độ các cảm biến:"
found=0
for zone in /sys/class/thermal/thermal_zone*/temp; do
    if [ -r "$zone" ]; then
        temp=$(cat "$zone" 2>/dev/null)
        if [ -n "$temp" ] && [ "$temp" -gt 0 ] 2>/dev/null; then
            zone_dir=$(dirname "$zone")
            name=$(cat "$zone_dir/type" 2>/dev/null || basename "$zone_dir")
            celsius=$((temp/1000))
            status="[OK]"
            if [ "$celsius" -ge 50 ] && [ "$celsius" -lt 65 ]; then status="[CẢNH BÁO]"; fi
            if [ "$celsius" -ge 65 ]; then status="[NÓNG]"; fi
            echo "  $status $name: ${celsius}°C"
            found=1
        fi
    fi
done
if [ "$found" -eq 0 ]; then
    echo "  Không phát hiện cảm biến nhiệt"
fi

# Battery via termux-api
if command -v termux-battery-status &> /dev/null; then
    echo ""
    echo "Thông tin Pin:"
    termux-battery-status 2>/dev/null | grep -E '"temperature"|"percentage"|"status"|"plugged"' | sed 's/[",]//g; s/^/  /'
fi

echo ""
echo "=========================================="
