# reinstall-debug.ps1 - Uninstall then reinstall the app

. "$PSScriptRoot\common.ps1"

Write-Header "REINSTALL DEBUG APK"

$adb = Get-AdbPath

if (-not (Test-DeviceConnected)) {
    Write-Fail "No device connected."
    exit 1
}

$serial = Get-DeviceSerial

# Uninstall
Write-Host "Uninstalling $PACKAGE_NAME..." -ForegroundColor Yellow
& $adb -s $serial uninstall $PACKAGE_NAME 2>&1 | ForEach-Object { Write-Host $_ }

# Build
Write-Host "Building debug APK..." -ForegroundColor Yellow
& "$PSScriptRoot\build-debug.ps1"

# Install
if (Test-Path $APK_DEBUG) {
    Write-Host "Installing..." -ForegroundColor Yellow
    & $adb -s $serial install $APK_DEBUG 2>&1 | ForEach-Object { Write-Host $_ }
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Reinstall complete"
        
        # Open app
        Write-Host "Launching app..." -ForegroundColor Yellow
        $component = "$PACKAGE_NAME/com.autoclicker.app.MainActivity"
        & $adb -s $serial shell am start -n $component
    } else {
        Write-Fail "Install failed"
    }
} else {
    Write-Fail "Build failed, cannot reinstall"
}
