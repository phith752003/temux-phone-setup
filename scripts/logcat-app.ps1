# logcat-app.ps1 - Show filtered logcat for Auto Clicker app

. "$PSScriptRoot\common.ps1"

Write-Header "LOGCAT - AUTO CLICKER"

$adb = Get-AdbPath

if (-not (Test-DeviceConnected)) {
    Write-Fail "No device connected."
    exit 1
}

$serial = Get-DeviceSerial

# Build tag filter from config
$tagFilter = ($LOG_TAGS | ForEach-Object { "${_}:D" }) -join " "
$tagFilter += " *:S"  # Silence all other tags

Write-Host "Filtering tags: $($LOG_TAGS -join ', ')" -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop" -ForegroundColor Gray
Write-Host ""

# Clear logcat buffer first
& $adb -s $serial logcat -c

# Start filtered logcat
& $adb -s $serial logcat -v time $tagFilter.Split(" ")
