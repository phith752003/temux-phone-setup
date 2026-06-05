#!/data/data/com.termux/files/usr/bin/bash
# Flicker-free real-time resource monitor for Termux Homelab
# Press 'q' to exit

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
UNDERLINE='\033[4m'
NC='\033[0m' # No Color

# Helper function to return progress bar string (doesn't echo directly)
get_bar_str() {
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

# Hide cursor and clear screen once at start
printf "\033[?25l"
clear

# Restore cursor and clear on exit
cleanup() {
    printf "\033[?25h"
    clear
    exit 0
}
trap cleanup SIGINT SIGTERM

while true; do
    # 1. Gather Metrics
    model=$(getprop ro.product.model 2>/dev/null || echo "Android Device")
    uptime_str=$(uptime -p 2>/dev/null | sed 's/up //')
    if [ -z "$uptime_str" ]; then uptime_str=$(uptime 2>/dev/null); fi
    
    load_str=$(cat /proc/loadavg 2>/dev/null | awk '{printf "1m: %s | 5m: %s | 15m: %s", $1, $2, $3}')
    
    # RAM
    read -r total_ram used_ram free_ram shared_ram buff_ram avail_ram <<< $(free 2>/dev/null | awk 'NR==2 {print $2, $3, $4, $5, $6, $7}')
    if [ -n "$total_ram" ] && [ "$total_ram" -gt 0 ] 2>/dev/null; then
        ram_pct=$(( used_ram * 100 / total_ram ))
        ram_total_h=$(free -h | awk 'NR==2 {print $2}')
        ram_used_h=$(free -h | awk 'NR==2 {print $3}')
    else
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
    
    # Disk
    read -r disk_total disk_used disk_avail disk_pct <<< $(df -h ~ | awk 'NR==2 {print $2, $3, $4, $5}')
    disk_pct_num=$(echo "$disk_pct" | tr -d '%')
    if [ -z "$disk_pct_num" ]; then disk_pct_num=0; fi

    # Battery
    bat_pct=""
    bat_status=""
    bat_temp=""
    if [ "$(uname -o 2>/dev/null)" = "Android" ] && command -v termux-battery-status &> /dev/null; then
        bat_info=$(timeout 1 termux-battery-status 2>/dev/null)
        if [ -n "$bat_info" ]; then
            bat_pct=$(echo "$bat_info" | grep '"percentage"' | tr -d '[:space:]",percentage:')
            bat_plugged=$(echo "$bat_info" | grep '"plugged"' | tr -d '[:space:]",plugged:')
            bat_temp=$(echo "$bat_info" | grep '"temperature"' | tr -d '[:space:]",temperature:')
            
            if [ "$bat_plugged" = "UNPLUGGED" ]; then
                bat_status="Discharging"
            else
                bat_status="Charging ($bat_plugged)"
            fi
        fi
    fi

    # SSH
    ssh_status="${RED}${BOLD}OFF${NC}"
    pgrep sshd >/dev/null 2>&1 && ssh_status="${GREEN}${BOLD}ON${NC}"

    # API
    api_status="${RED}${BOLD}OFF${NC}"
    timeout 1 bash -c '</dev/tcp/127.0.0.1/3000' 2>/dev/null && api_status="${GREEN}${BOLD}ON${NC}"

    # tmux
    sessions_count=$(tmux ls 2>/dev/null | wc -l)
    if [ "$sessions_count" -gt 0 ]; then
        tmux_status="${GREEN}${BOLD}$sessions_count active${NC}"
    else
        tmux_status="${YELLOW}none${NC}"
    fi

    # Temps
    cpu_temp=$(get_sensor_temp "cpu-1-1-usr")
    cpuss_temp=$(get_sensor_temp "cpuss-3-usr")
    gpu_temp=$(get_sensor_temp "gpu-usr")

    # 2. Build output buffer
    buf=""
    buf="${buf}${CYAN}╔══════════════════════════════════════════════════════════════════╗\033[K\n"
    buf="${buf}║${BOLD}                  REAL-TIME HOMELAB MONITOR                       ${NC}${CYAN}║\033[K\n"
    buf="${buf}╚══════════════════════════════════════════════════════════════════╝\033[K\n"
    buf="${buf}  Time:      $(date '+%Y-%m-%d %H:%M:%S')  | Device: $model\033[K\n"
    buf="${buf}  Uptime:    $uptime_str  | Load:   $load_str\033[K\n"
    if [ -n "$bat_pct" ]; then
        buf="${buf}  Battery:   $bat_pct% [$bat_status] | Temp:   ${bat_temp}°C\033[K\n"
    fi
    buf="${buf}${CYAN}────────────────────────────────────────────────────────────────────${NC}\033[K\n"
    
    # Progress bars
    ram_bar=$(get_bar_str $ram_pct)
    buf="${buf}  💾 RAM:  ${ram_bar}          (Used: ${ram_used_h} / Total: ${ram_total_h})\033[K\n"
    
    disk_bar=$(get_bar_str $disk_pct_num)
    buf="${buf}  💿 DISK: ${disk_bar}          (Used: ${disk_used} / Total: ${disk_total})\033[K\n"

    buf="${buf}${CYAN}────────────────────────────────────────────────────────────────────${NC}\033[K\n"
    buf="${buf}  🟢 Services: SSH [${ssh_status}]  API [${api_status}]  tmux [${tmux_status}]\033[K\n"
    
    if [ -n "$cpu_temp" ] || [ -n "$cpuss_temp" ]; then
        buf="${buf}  🌡️  Temps:    CPU Core: ${cpu_temp:-N/A}°C | Cluster: ${cpuss_temp:-N/A}°C | GPU: ${gpu_temp:-N/A}°C\033[K\n"
    fi

    buf="${buf}${CYAN}────────────────────────────────────────────────────────────────────${NC}\033[K\n"

    # Top Processes
    buf="${buf}  📊 ${BOLD}Top 5 Processes by CPU:${NC}\033[K\n"
    buf="${buf}     ${UNDERLINE}PID      CPU%%    COMMAND${NC}\033[K\n"
    
    # Capture top 5 processes output and append it line by line
    top_proc=$(ps aux 2>/dev/null | grep -v -E " (ps|sort|head|awk|monitor\.sh)( |$)" | sort -k3 -rn | head -5 | awk '{printf "     %-8s %-8s %s\033[K\n", $2, $3"%", $11}' 2>/dev/null)
    if [ -n "$top_proc" ]; then
        buf="${buf}${top_proc}\n"
    else
        buf="${buf}     N/A\033[K\n"
    fi

    buf="${buf}${CYAN}────────────────────────────────────────────────────────────────────${NC}\033[K\n"
    buf="${buf}  ${YELLOW}Press [q] to exit monitoring...${NC}\033[K\n"
    buf="${buf}${CYAN}╚══════════════════════════════════════════════════════════════════╝${NC}\033[K\n"

    # Move cursor to home (top-left) and print buffer at once
    printf "\033[H"
    echo -ne "$buf"

    # Non-blocking key check: wait for 2 seconds, check if key 'q' pressed
    read -t 2 -n 1 key
    read_status=$?
    if [ $read_status -ne 0 ] && [ $read_status -lt 128 ]; then
        # Stdin is not a TTY or read failed instantly, sleep to prevent 100% CPU spinning
        sleep 2
    fi
    if [[ $key == "q" || $key == "Q" ]]; then
        break
    fi
done

cleanup
