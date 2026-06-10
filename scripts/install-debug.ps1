# install-debug.ps1 - Install debug APK to connected device

. "$PSScriptRoot\common.ps1"

Write-Header "INSTALL DEBUG APK"

$adb = Get-AdbPath

# Check device
if (-not (Test-DeviceConnected)) {
    Write-Fail "No device connected."
    exit 1
}

# Check APK exists
if (-not (Test-Path $APK_DEBUG)) {
    Write-Host "APK not found. Building first..." -ForegroundColor Yellow
    & "$PSScriptRoot\build-debug.ps1"
    
    if (-not (Test-Path $APK_DEBUG)) {
        Write-Fail "Build failed. Cannot install."
        exit 1
    }
}

Write-Host "Installing APK..." -ForegroundColor Yellow
$serial = Get-DeviceSerial
& $adb -s $serial install -r $APK_DEBUG 2>&1 | ForEach-Object { Write-Host $_ }

if ($LASTEXITCODE -eq 0) {
    Write-Success "APK installed on $serial"
} else {
    Write-Fail "Install failed. Try: .\scripts\reinstall-debug.ps1"
}
