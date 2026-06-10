# build-debug.ps1 - Build debug APK

. "$PSScriptRoot\common.ps1"

Write-Header "BUILD DEBUG APK"

if (-not (Test-Path $PROJECT_DIR)) {
    Write-Fail "Project directory not found: $PROJECT_DIR"
    exit 1
}

# Set ANDROID_HOME if not set
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = $SDK_DIR
    Write-Host "Set ANDROID_HOME=$SDK_DIR" -ForegroundColor Yellow
}

Write-Host "Building..." -ForegroundColor Yellow
Push-Location $PROJECT_DIR

try {
    & .\gradlew.bat assembleDebug --no-daemon 2>&1 | ForEach-Object { Write-Host $_ }
    
    if (Test-Path $APK_DEBUG) {
        $size = [math]::Round((Get-Item $APK_DEBUG).Length / 1MB, 2)
        Write-Host ""
        Write-Success "APK built successfully!"
        Write-Host "  Path: $APK_DEBUG" -ForegroundColor Green
        Write-Host "  Size: ${size} MB" -ForegroundColor Green
    } else {
        Write-Fail "APK not found at expected path: $APK_DEBUG"
    }
} catch {
    Write-Fail "Build failed: $_"
} finally {
    Pop-Location
}
