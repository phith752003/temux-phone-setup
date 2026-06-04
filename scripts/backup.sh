#!/data/data/com.termux/files/usr/bin/bash
# Sao lưu dữ liệu homelab

BACKUP_DIR="$HOME/homelab-backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/homelab-backup-$TIMESTAMP.tar.gz"

mkdir -p "$BACKUP_DIR"

echo "Đang sao lưu homelab..."

# Backup data + apis + scripts + websites
tar -czf "$BACKUP_FILE" \
    -C "$HOME" \
    homelab/data \
    homelab/apis \
    homelab/scripts \
    homelab/websites \
    homelab/README.md \
    2>/dev/null

if [ $? -eq 0 ]; then
    SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
    echo "Sao lưu thành công: $BACKUP_FILE ($SIZE)"
else
    echo "Sao lưu thất bại!"
    exit 1
fi

# Git push nếu có remote
cd ~/homelab
if git remote -v 2>/dev/null | grep -q origin; then
    git add -A
    git commit -m "Backup $TIMESTAMP" 2>/dev/null
    git push 2>/dev/null && echo "Đã đồng bộ lên GitHub thành công" || echo "Đồng bộ GitHub thất bại (vui lòng kiểm tra lại cấu hình remote)"
else
    echo "Không tìm thấy cấu hình git remote. Bỏ qua đồng bộ GitHub."
fi

# Dọn dẹp các bản sao lưu cũ hơn 7 ngày
deleted=$(find "$BACKUP_DIR" -name "*.tar.gz" -mtime +7 -delete -print | wc -l)
echo "Đã dọn dẹp $deleted bản sao lưu cũ hơn 7 ngày"
