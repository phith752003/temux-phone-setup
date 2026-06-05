#!/data/data/com.termux/files/usr/bin/bash
# High-fidelity system status reporter for Termux Homelab

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
UNDERLINE='\033[4m'
NC='\033[0m' # No Color

# Helper function to draw progress bars
draw_bar() {
    local percent=$1
    local width=20
    local filled=$(( percent * width / 100 ))
    local empty=$(( width - filled ))
    local bar=""
    
    for ((i=0; i<filled; i++)); do bar="${bar}█"; done
    for ((i=0; i<empty; i++)); do bar="${bar}░"; done
    
    if [ "$percent" -ge 85 ]; then
        echo -e "${RED}[${bar}] ${percent}%${NC}"
    elif [ "$percent" -ge 70 ]; then
        echo -e "${YELLOW}[${bar}] ${percent}%${NC}"
    else
        echo -e "${GREEN}[${bar}] ${percent}%${NC}"
    fi
}

# Helper to read thermal zone temperature
get_sensor_temp() {
    local sensor=$1
    for zone in /sys/class/thermal/thermal_zone*; do
        if [ -d "$zone" ]; then
            type=$(cat "$zone/type" 2>/dev/null)
            if [ "$type" = "$sensor" ]; then
                temp=$(cat "$zone/temp" 2>/dev/null)
                if [ -n "$temp" ] && [ "$temp" -gt 0 ] 2>/dev/null; then
                    echo "$((temp/1000))"
                    return
                fi
            fi
        fi
    done
    echo ""
}

# 1. Gather System Metrics
# OS & Model
model=$(getprop ro.product.model 2>/dev/null || echo "Android Device")
uptime_str=$(uptime -p 2>/dev/null | sed 's/up //')
if [ -z "$uptime_str" ]; then uptime_str=$(uptime 2>/dev/null); fi

# CPU Load
load_str=$(cat /proc/loadavg 2>/dev/null | awk '{printf "1m: %s | 5m: %s | 15m: %s", $1, $2, $3}')

# RAM Info
read -r total_ram used_ram free_ram shared_ram buff_ram avail_ram <<< $(free 2>/dev/null | awk 'NR==2 {print $2, $3, $4, $5, $6, $7}')
if [ -n "$total_ram" ] && [ "$total_ram" -gt 0 ] 2>/dev/null; then
    ram_pct=$(( used_ram * 100 / total_ram ))
    ram_total_h=$(free -h | awk 'NR==2 {print $2}')
    ram_used_h=$(free -h | awk 'NR==2 {print $3}')
else
    # Fallback to /proc/meminfo
    total_kb=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    avail_kb=$(grep MemAvailable /proc/meminfo | awk '{print $2}')
    if [ -n "$total_kb" ] && [ "$total_kb" -gt 0 ] 2>/dev/null; then
        used_kb=$((total_kb - avail_kb))
        ram_pct=$((used_kb * 100 / total_kb))
        ram_total_h="$((total_kb/1024/1024))G"
        ram_used_h="$((used_kb/1024/1024))G"
    else
        ram_pct=0
        ram_total_h="N/A"
        ram_used_h="N/A"
    fi
fi

# Disk Space
read -r disk_total disk_used disk_avail disk_pct <<< $(df -h ~ | awk 'NR==2 {print $2, $3, $4, $5}')
disk_pct_num=$(echo "$disk_pct" | tr -d '%')
if [ -z "$disk_pct_num" ]; then disk_pct_num=0; fi

# Battery Info (termux-api fallback)
bat_pct=""
bat_status=""
bat_temp=""
if [ "$(uname -o 2>/dev/null)" = "Android" ] && command -v termux-battery-status &> /dev/null; then
    bat_info=$(timeout 1 termux-battery-status 2>/dev/null)
    if [ -n "$bat_info" ]; then
        bat_pct=$(echo "$bat_info" | grep '"percentage"' | tr -d '[:space:]",percentage:')
        bat_plugged=$(echo "$bat_info" | grep '"plugged"' | tr -d '[:space:]",plugged:')
        bat_temp=$(echo "$bat_info" | grep '"temperature"' | tr -d '[:space:]",temperature:')
        bat_status_raw=$(echo "$bat_info" | grep '"status"' | tr -d '[:space:]",status:')
        
        # Format plug status
        if [ "$bat_plugged" = "UNPLUGGED" ]; then
            bat_status="🔋 discharging"
        else
            bat_status="🔌 charging via $bat_plugged"
        fi
    fi
fi

# 2. Render beautiful Dashboard
echo -e "${CYAN}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║${BOLD}                   HOMELAB STATUS - VSMART LIVE 4                 ${NC}${CYAN}║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════════╝${NC}"

# Device profile
echo -e "  📱 ${BOLD}Device Model:${NC} $model (${BOLD}OS:${NC} Android $(getprop ro.build.version.release 2>/dev/null || echo "10+"))"
echo -e "  ⏱️  ${BOLD}Uptime:${NC}       $uptime_str"
echo -e "  📊 ${BOLD}CPU Load:${NC}     $load_str"

if [ -n "$bat_pct" ]; then
    echo -e "  🔋 ${BOLD}Battery Status:${NC} $bat_pct% [$bat_status] [Temp: ${bat_temp}°C]"
fi

echo -e "${CYAN}────────────────────────────────────────────────────────────────────${NC}"

# RAM & Disk bars
printf "  💾 ${BOLD}RAM Usage:${NC}     "
draw_bar $ram_pct
echo -e "                     (Used: ${ram_used_h} / Total: ${ram_total_h})"
echo ""
printf "  💿 ${BOLD}Disk Storage:${NC}  "
draw_bar $disk_pct_num
echo -e "                     (Used: ${disk_used} / Total: ${disk_total} - Free: ${disk_avail})"

echo -e "${CYAN}────────────────────────────────────────────────────────────────────${NC}"

# Temperatures
echo -e "  🌡️  ${BOLD}Sensors Temperature:${NC}"
cpu_temp=$(get_sensor_temp "cpu-1-1-usr")
cpuss_temp=$(get_sensor_temp "cpuss-3-usr")
gpu_temp=$(get_sensor_temp "gpu-usr")
sdm_temp=$(get_sensor_temp "sdm-therm")

# Fallback checking if specific zones are not found
if [ -z "$cpu_temp" ] && [ -z "$cpuss_temp" ]; then
    # Print first 2 generic thermal zones
    count=0
    for zone in /sys/class/thermal/thermal_zone*; do
        if [ "$count" -lt 4 ]; then
            type=$(cat "$zone/type" 2>/dev/null)
            temp=$(cat "$zone/temp" 2>/dev/null)
            if [ -n "$type" ] && [ -n "$temp" ] && [ "$temp" -gt 0 ] 2>/dev/null; then
                printf "     %-15s: %s°C" "$type" "$((temp/1000))"
                count=$((count+1))
                if [ $((count % 2)) -eq 0 ]; then echo ""; fi
            fi
        fi
    done
    if [ $count -gt 0 ] && [ $((count % 2)) -ne 0 ]; then echo ""; fi
else
    # Nicely aligned specific sensors
    printf "     %-15s: %s      %-15s: %s\n" "• CPU Core" "${cpu_temp:---}°C" "• CPU cluster" "${cpuss_temp:---}°C"
    printf "     %-15s: %s      %-15s: %s\n" "• GPU Core" "${gpu_temp:---}°C" "• System Board" "${sdm_temp:---}°C"
fi

echo -e "${CYAN}────────────────────────────────────────────────────────────────────${NC}"

# Services Status
echo -e "  🟢 ${BOLD}Active Services:${NC}"

# SSH
printf "     %-28s" "• SSH Daemon (Port 8022):"
if pgrep sshd > /dev/null 2>&1; then
    echo -e "${GREEN}${BOLD}🟢 ONLINE${NC}"
else
    echo -e "${RED}${BOLD}🔴 OFFLINE${NC}"
fi

# API
printf "     %-28s" "• API Dashboard (Port 3000):"
if timeout 1 bash -c '</dev/tcp/127.0.0.1/3000' 2>/dev/null; then
    echo -e "${GREEN}${BOLD}🟢 ONLINE${NC}"
else
    echo -e "${RED}${BOLD}🔴 OFFLINE${NC}"
fi

# Tmux
printf "     %-28s" "• tmux Daemon Status:"
sessions_count=$(tmux ls 2>/dev/null | wc -l)
if [ "$sessions_count" -gt 0 ]; then
    echo -e "${GREEN}${BOLD}🟢 ACTIVE ($sessions_count session(s))${NC}"
    tmux ls 2>/dev/null | awk -F: '{print "       └─ " $1}'
else
    echo -e "${YELLOW}🟡 NO ACTIVE SESSIONS${NC}"
fi

echo -e "${CYAN}╚══════════════════════════════════════════════════════════════════╝${NC}"
