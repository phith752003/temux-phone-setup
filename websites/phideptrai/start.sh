#!/data/data/com.termux/files/usr/bin/bash
PORT=8080
SESSION="static-web"
echo "Đang chạy static web server trên cổng $PORT..."
tmux new-session -d -s "$SESSION" "python3 -m http.server $PORT --directory $HOME/homelab/websites/$(basename "$PWD")/public"
echo "Đã khởi chạy static web server trong tmux session '$SESSION'!"
echo "Truy cập cục bộ tại địa chỉ: http://localhost:$PORT"
