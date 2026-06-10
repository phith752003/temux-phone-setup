# open-app.ps1 - Launch the Auto Clicker app on device

. "$PSScriptRoot\common.ps1"

Write-Header "OPEN APP"

$adb = Get-AdbPath

if (-not (Test-DeviceConnected)) {
    Write-Fail "No device connected."
    exit 1
}

$serial = Get-DeviceSerial
$component = "com.autoclicker.app.debug/com.autoclicker.app.MainActivity"

Write-Host "Launching $component on $serial..." -ForegroundColor Yellow
& $adb -s $serial shell am start -n $component 2>&1 | ForEach-Object { Write-Host $_ }

if ($LASTEXITCODE -eq 0) {
    Write-Success "App launched"
} else {
    Write-Fail "Failed to launch app. Is it installed?"
}
