#!/data/data/com.termux/files/usr/bin/bash
# Setup script - Chạy trên Termux để cài đặt mọi thứ tự động
# Dùng: bash setup.sh

set -e

echo "╔══════════════════════════════════════╗"
echo "║  HOMELAB SETUP – Vsmart Live 4   ║"
echo "╚══════════════════════════════════════╝"
echo ""

# Phase 3: Cài packages
echo "[1/5] Cài bộ công cụ..."
pkg update -y
pkg install openssh git curl wget nano vim tmux nodejs python sqlite htop net-tools termux-api -y

echo ""
echo "[2/5] Cài Python packages..."
# Tải và cài đặt pydantic-core pre-compiled bằng cách đổi tên tag tương thích
wget https://github.com/Eutalix/android-pydantic-core/releases/download/v2.46.3/pydantic_core-2.46.3-cp313-cp313-linux_aarch64.whl
mv pydantic_core-2.46.3-cp313-cp313-linux_aarch64.whl pydantic_core-2.46.3-cp313-cp313-android_arm64_v8a.whl
pip install pydantic_core-2.46.3-cp313-cp313-android_arm64_v8a.whl
rm -f pydantic_core-2.46.3-cp313-cp313-android_arm64_v8a.whl
pip install fastapi uvicorn "pydantic-core==2.46.3" slowapi jinja2

# Phase 5: Tạo cấu trúc thư mục
echo ""
echo "[3/5] Tạo cấu trúc homelab..."
mkdir -p ~/homelab/{apis/test-api,bots,data,logs,scripts,tunnels}

# Copy files nếu đã push qua ADB/SCP
SRC_DIR=""
if [ -d "$HOME/temux-install" ]; then
    SRC_DIR="$HOME/temux-install"
elif [ -d "/sdcard/temux-install" ]; then
    SRC_DIR="/sdcard/temux-install"
fi

if [ -n "$SRC_DIR" ]; then
    echo "  Tìm thấy files tại $SRC_DIR, copying..."
    cp -r "$SRC_DIR"/apis/* ~/homelab/apis/ 2>/dev/null || true
    cp -r "$SRC_DIR"/bots/* ~/homelab/bots/ 2>/dev/null || true
    cp -r "$SRC_DIR"/scripts/* ~/homelab/scripts/ 2>/dev/null || true
    cp "$SRC_DIR"/README.md ~/homelab/ 2>/dev/null || true
fi

# Phase 9: Tạo boot directory
echo ""
echo "[4/5] Cấu hình auto-start..."
mkdir -p ~/.termux/boot
if [ -n "$SRC_DIR" ] && [ -f "$SRC_DIR/boot/start-homelab.sh" ]; then
    cp "$SRC_DIR/boot/start-homelab.sh" ~/.termux/boot/
fi

# Set permissions
echo ""
echo "[5/5] Đặt quyền thực thi..."
chmod +x ~/homelab/scripts/*.sh 2>/dev/null
chmod +x ~/.termux/boot/*.sh 2>/dev/null

echo ""
echo "╔═══════════════════════╗"
echo "║ SETUP HOÀN THÀNH!   ║"
echo "╚═══════════════════════╝"
echo ""
echo "Kiểm tra:"
echo "  node -v && python --version && git --version"
echo ""
echo "Bước tiếp theo:"
echo "  1. Đặt mật khẩu SSH:  passwd"
echo "  2. Bật SSH server:     sshd"
echo "  3. Xem IP:             ip addr show wlan0"
echo "  4. Chạy API:           bash ~/homelab/scripts/start-api.sh"
echo "  5. Kiểm tra:           bash ~/homelab/scripts/status.sh"
