# common.ps1 - Shared variables and functions for Auto Clicker build/deploy scripts
# Usage: . "$PSScriptRoot\common.ps1"

$ErrorActionPreference = "Continue"

# --- Paths ---
$REPO_ROOT = Split-Path -Parent $PSScriptRoot
$CONFIG_FILE = Join-Path $REPO_ROOT "config\autoclicker.json"
$PROJECT_DIR = Join-Path $REPO_ROOT "auto-clicker"

# --- Load config ---
if (Test-Path $CONFIG_FILE) {
    $Config = Get-Content $CONFIG_FILE -Raw | ConvertFrom-Json
    $PACKAGE_NAME = $Config.package_name
    $MAIN_ACTIVITY = $Config.main_activity
    $ADB_PATH = $Config.adb_path
    $ADB_PATH_FALLBACK = Join-Path $REPO_ROOT $Config.adb_path_fallback
    $SDK_DIR = $Config.sdk_dir
    $LOG_TAGS = $Config.log_tags
} else {
    Write-Host "[WARN] Config file not found: $CONFIG_FILE. Using defaults." -ForegroundColor Yellow
    $PACKAGE_NAME = "com.autoclicker.app"
    $MAIN_ACTIVITY = ".MainActivity"
    $ADB_PATH = "C:\platform-tools\adb.exe"
    $ADB_PATH_FALLBACK = Join-Path $REPO_ROOT "platform-tools-dist\platform-tools\adb.exe"
    $SDK_DIR = "C:\Android\sdk"
    $LOG_TAGS = @("AutoClickerApp", "MacroRunner", "GestureService", "OverlayService", "BatteryMonitor", "ThermalMonitor")
}

# --- Resolve ADB ---
function Get-AdbPath {
    if (Test-Path $ADB_PATH) {
        return $ADB_PATH
    } elseif (Test-Path $ADB_PATH_FALLBACK) {
        Write-Host "[INFO] Using fallback ADB: $ADB_PATH_FALLBACK" -ForegroundColor Yellow
        return $ADB_PATH_FALLBACK
    } else {
        Write-Host "[ERROR] ADB not found at $ADB_PATH or $ADB_PATH_FALLBACK" -ForegroundColor Red
        exit 1
    }
}

# --- Check device connected ---
function Test-DeviceConnected {
    $adb = Get-AdbPath
    $devices = & $adb devices 2>&1
    if ($devices -match "device$") {
        return $true
    }
    return $false
}

# --- Get device serial ---
function Get-DeviceSerial {
    $adb = Get-AdbPath
    $lines = & $adb devices 2>&1 | Select-String "device$"
    if ($lines) {
        return ($lines[0] -split "\t")[0]
    }
    return $null
}

# --- APK path ---
$APK_DEBUG = Join-Path $PROJECT_DIR "app\build\outputs\apk\debug\app-debug.apk"

# --- Helper functions ---
function Write-Header {
    param([string]$Title)
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host "=========================================" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}
