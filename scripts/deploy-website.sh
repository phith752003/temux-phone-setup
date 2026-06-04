#!/data/data/com.termux/files/usr/bin/bash
# deploy-website.sh - Khởi tạo nhanh dự án Web trên Termux

set -e

WEBSITES_DIR="$HOME/homelab/websites"
mkdir -p "$WEBSITES_DIR"

echo "========================================="
echo "  KHỞI TẠO DỰ ÁN WEB MỚI TRÊN TERMUX     "
echo "========================================="
echo ""

read -p "Nhập tên dự án web (ví dụ: my-site): " SITE_NAME
if [ -z "$SITE_NAME" ]; then
    echo "Lỗi: Tên dự án không được để trống!"
    exit 1
fi

SITE_DIR="$WEBSITES_DIR/$SITE_NAME"

if [ -d "$SITE_DIR" ]; then
    echo "Cảnh báo: Thư mục $SITE_NAME đã tồn tại."
    read -p "Bạn có muốn ghi đè/dọn dẹp và tạo lại không? (y/N): " OVERWRITE
    if [[ "$OVERWRITE" =~ ^[Yy]$ ]]; then
        rm -rf "$SITE_DIR"
    else
        echo "Đã hủy thao tác."
        exit 0
    fi
fi

mkdir -p "$SITE_DIR"

echo "Chọn loại dự án:"
echo "1) HTML/CSS/JS Tĩnh (Static Web Server)"
echo "2) Node.js + Express.js API/Website"
echo "3) Python + FastAPI"
read -p "Nhập lựa chọn của bạn (1-3): " CHOICE

case $CHOICE in
    1)
        # HTML Tĩnh
        echo "Khởi tạo Static HTML Website..."
        mkdir -p "$SITE_DIR/public"
        cat << 'EOF' > "$SITE_DIR/public/index.html"
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Xin chào từ Android Homelab</title>
    <style>
        body {
            background-color: #0f172a;
            color: #f8fafc;
            font-family: system-ui, -apple-system, sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
            margin: 0;
        }
        .card {
            text-align: center;
            background: rgba(30, 41, 59, 0.7);
            padding: 2.5rem;
            border-radius: 16px;
            border: 1px solid rgba(255, 255, 255, 0.1);
            box-shadow: 0 10px 25px rgba(0,0,0,0.3);
            backdrop-filter: blur(10px);
        }
        h1 {
            background: linear-gradient(to right, #38bdf8, #818cf8);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            margin-bottom: 0.5rem;
        }
        p { color: #94a3b8; }
        .badge {
            background: rgba(16, 185, 129, 0.2);
            color: #34d399;
            padding: 0.25rem 0.75rem;
            border-radius: 9999px;
            font-size: 0.85rem;
            display: inline-block;
            margin-top: 1rem;
        }
    </style>
</head>
<body>
    <div class="card">
        <h1>Android Homelab Mini</h1>
        <p>Website tĩnh của bạn đang chạy native trên Android!</p>
        <div class="badge">Trạng thái: ONLINE</div>
    </div>
</body>
</html>
EOF
        cat << 'EOF' > "$SITE_DIR/start.sh"
#!/data/data/com.termux/files/usr/bin/bash
PORT=8080
SESSION="static-web"
echo "Đang chạy static web server trên cổng $PORT..."
tmux new-session -d -s "$SESSION" "python3 -m http.server $PORT --directory $HOME/homelab/websites/$(basename "$PWD")/public"
echo "Đã khởi chạy static web server trong tmux session '$SESSION'!"
echo "Truy cập cục bộ tại địa chỉ: http://localhost:$PORT"
EOF
        chmod +x "$SITE_DIR/start.sh"
        ;;
    2)
        # Node Express
        echo "Khởi tạo Node.js Express Website..."
        cd "$SITE_DIR"
        npm init -y > /dev/null
        npm install express > /dev/null
        
        cat << 'EOF' > "$SITE_DIR/index.js"
const express = require('express');
const app = express();
const port = 8080;

app.get('/', (req, res) => {
    res.send(`
        <div style="text-align: center; padding: 50px; font-family: sans-serif; background-color: #0f172a; color: white; min-height: 100vh;">
            <h1 style="color: #38bdf8;">Node.js Express App</h1>
            <p>Ứng dụng Node.js đang chạy trên Android Homelab!</p>
            <p>Thời gian máy chủ: ${new Date().toLocaleString()}</p>
        </div>
    `);
});

app.listen(port, () => {
    console.log(`App running at http://localhost:${port}`);
});
EOF
        cat << 'EOF' > "$SITE_DIR/start.sh"
#!/data/data/com.termux/files/usr/bin/bash
SESSION="node-app"
echo "Đang chạy Express app..."
tmux new-session -d -s "$SESSION" -c "$HOME/homelab/websites/$(basename "$PWD")" "node index.js"
echo "Đã khởi chạy Node Express trong tmux session '$SESSION'!"
echo "Truy cập cục bộ tại địa chỉ: http://localhost:8080"
EOF
        chmod +x "$SITE_DIR/start.sh"
        ;;
    3)
        # FastAPI
        echo "Khởi tạo Python FastAPI Website..."
        cat << 'EOF' > "$SITE_DIR/main.py"
from fastapi import FastAPI
from fastapi.responses import HTMLResponse

app = FastAPI()

@app.get("/", response_class=HTMLResponse)
def home():
    return """
    <div style="text-align: center; padding: 50px; font-family: sans-serif; background-color: #0f172a; color: white; min-height: 100vh;">
        <h1 style="color: #818cf8;">Python FastAPI App</h1>
        <p>FastAPI server đang chạy native trên Android Homelab!</p>
    </div>
    """
EOF
        cat << 'EOF' > "$SITE_DIR/start.sh"
#!/data/data/com.termux/files/usr/bin/bash
SESSION="fastapi-app"
echo "Đang chạy FastAPI app..."
tmux new-session -d -s "$SESSION" -c "$HOME/homelab/websites/$(basename "$PWD")" "uvicorn main:app --host 0.0.0.0 --port 8080"
echo "Đã khởi chạy FastAPI trong tmux session '$SESSION'!"
echo "Truy cập cục bộ tại địa chỉ: http://localhost:8080"
EOF
        chmod +x "$SITE_DIR/start.sh"
        ;;
    *)
        echo "Lỗi: Lựa chọn không hợp lệ."
        exit 1
        ;;
esac

echo ""
echo "Dự án đã được khởi tạo thành công tại: $SITE_DIR"
echo "Để chạy máy chủ, thực hiện: bash $SITE_DIR/start.sh"
