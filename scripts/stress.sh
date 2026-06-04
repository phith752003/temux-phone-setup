#!/data/data/com.termux/files/usr/bin/bash
# Script stress test CPU an toàn sử dụng Python
# Cách dùng: bash stress.sh <số_cores> <thời_gian_giây>
# Ví dụ: bash stress.sh 2 60

CORES=${1:-2}
DURATION=${2:-60}

echo "Bắt đầu Stress Test: $CORES cores trong $DURATION giây..."
echo "Bật monitor.sh ở cửa sổ khác để theo dõi nhiệt độ."

python3 -c "
import multiprocessing, time, os

def stress_core():
    # Vòng lặp tính toán để đẩy CPU lên 100%
    x = 0
    while True:
        x = (x + 1) * (x - 1)

if __name__ == '__main__':
    processes = []
    for i in range($CORES):
        p = multiprocessing.Process(target=stress_core)
        p.start()
        processes.append(p)
        print(f'  -> Khởi chạy core stress process {i+1} (PID: {p.pid})')
    
    # Đợi thời gian quy định
    time.sleep($DURATION)
    
    # Dừng tất cả
    print('Hết thời gian test, đang dừng các process...')
    for p in processes:
        p.terminate()
        p.join()
        
    print('Đã dừng toàn bộ CPU stress processes thành công.')
"
echo "=== Stress Test Hoàn Thành ==="
