# start-adb-forward.ps1
# Script PowerShell chạy trên Windows để khởi tạo nhanh ADB Port Forwarding

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  ĐANG KHỞI TẠO ADB PORT FORWARDING..." -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Kiểm tra thiết bị có kết nối adb không
$devices = & "C:\platform-tools\adb.exe" devices
if ($devices -match "device$") {
    # Thực hiện forward các cổng
    & "C:\platform-tools\adb.exe" forward tcp:8022 tcp:8022
    & "C:\platform-tools\adb.exe" forward tcp:3000 tcp:3000
    & "C:\platform-tools\adb.exe" forward tcp:8080 tcp:8080

    Write-Host ""
    Write-Host "ĐÃ FORWARD CÁC CỔNG THÀNH CÔNG:" -ForegroundColor Green
    Write-Host "   - Cổng SSH:       127.0.0.1:8022  -->  Termux:8022" -ForegroundColor Yellow
    Write-Host "   - Cổng Dashboard: 127.0.0.1:3000  -->  Termux:3000" -ForegroundColor Yellow
    Write-Host "   - Cổng Web Test:  127.0.0.1:8080  -->  Termux:8080" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Bạn có thể kết nối VS Code Remote-SSH tới host 'homelab' ngay bây giờ." -ForegroundColor Green
    Write-Host "Dashboard hoạt động tại địa chỉ: http://127.0.0.1:3000" -ForegroundColor Green
} else {
    Write-Host "KHÔNG TÌM THẤY ĐIỆN THOẠI KẾT NỐI ADB!" -ForegroundColor Red
    Write-Host "   Vui lòng kiểm tra cáp USB và đảm bảo USB Debugging đã được bật." -ForegroundColor Red
}
Write-Host "=========================================" -ForegroundColor Cyan
pause
