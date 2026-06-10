# adb-devices.ps1 - List connected ADB devices
# Pattern reused from start-adb-forward.ps1

. "$PSScriptRoot\common.ps1"

Write-Header "ADB DEVICES"

$adb = Get-AdbPath
Write-Host "Using ADB: $adb" -ForegroundColor Gray
Write-Host ""

$output = & $adb devices -l 2>&1
Write-Host $output

$serial = Get-DeviceSerial
if ($serial) {
    Write-Success "Device found: $serial"
} else {
    Write-Fail "No device connected. Check USB cable and USB Debugging."
}
