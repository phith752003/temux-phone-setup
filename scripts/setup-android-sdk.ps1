# setup-android-sdk.ps1
# One-time setup: Download Android SDK command-line tools, Gradle, and initialize project
# Run as Administrator if needed for environment variable changes

. "$PSScriptRoot\common.ps1"

Write-Header "ANDROID SDK & GRADLE SETUP"

$SDK_DIR = "C:\Android\sdk"
$CMDLINE_TOOLS_DIR = "$SDK_DIR\cmdline-tools\latest"
$GRADLE_VERSION = "8.7"
$GRADLE_DIR = "C:\Gradle\gradle-$GRADLE_VERSION"

# ============================================
# Step 1: Create SDK directory
# ============================================
Write-Host "`n[1/6] Creating SDK directory..." -ForegroundColor Yellow
if (-not (Test-Path $SDK_DIR)) {
    New-Item -ItemType Directory -Path $SDK_DIR -Force | Out-Null
    Write-Success "Created $SDK_DIR"
} else {
    Write-Host "  SDK directory already exists: $SDK_DIR" -ForegroundColor Gray
}

# ============================================
# Step 2: Download Android Command-Line Tools
# ============================================
Write-Host "`n[2/6] Downloading Android Command-Line Tools..." -ForegroundColor Yellow
$CMDLINE_TOOLS_URL = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
$CMDLINE_TOOLS_ZIP = "$env:TEMP\cmdline-tools.zip"

if (-not (Test-Path "$CMDLINE_TOOLS_DIR\bin\sdkmanager.bat")) {
    Write-Host "  Downloading from $CMDLINE_TOOLS_URL ..."
    Invoke-WebRequest -Uri $CMDLINE_TOOLS_URL -OutFile $CMDLINE_TOOLS_ZIP -UseBasicParsing
    
    # Extract to temp first, then move to correct location
    $EXTRACT_TEMP = "$env:TEMP\cmdline-tools-extract"
    if (Test-Path $EXTRACT_TEMP) { Remove-Item $EXTRACT_TEMP -Recurse -Force }
    Expand-Archive -Path $CMDLINE_TOOLS_ZIP -DestinationPath $EXTRACT_TEMP -Force
    
    # Move to SDK/cmdline-tools/latest/
    if (-not (Test-Path "$SDK_DIR\cmdline-tools")) {
        New-Item -ItemType Directory -Path "$SDK_DIR\cmdline-tools" -Force | Out-Null
    }
    if (Test-Path $CMDLINE_TOOLS_DIR) { Remove-Item $CMDLINE_TOOLS_DIR -Recurse -Force }
    Move-Item "$EXTRACT_TEMP\cmdline-tools" $CMDLINE_TOOLS_DIR
    
    # Cleanup
    Remove-Item $CMDLINE_TOOLS_ZIP -Force -ErrorAction SilentlyContinue
    Remove-Item $EXTRACT_TEMP -Recurse -Force -ErrorAction SilentlyContinue
    
    Write-Success "Command-line tools installed"
} else {
    Write-Host "  Command-line tools already installed" -ForegroundColor Gray
}

# ============================================
# Step 3: Accept licenses and install SDK components
# ============================================
Write-Host "`n[3/6] Installing SDK components (build-tools, platforms)..." -ForegroundColor Yellow
$SDKMANAGER = "$CMDLINE_TOOLS_DIR\bin\sdkmanager.bat"

# Accept all licenses
Write-Host "  Accepting licenses..."
$yesStream = ("y`n" * 20)
$yesStream | & $SDKMANAGER --licenses --sdk_root="$SDK_DIR" 2>&1 | Out-Null

# Install required packages
$packages = @(
    "platform-tools",
    "build-tools;34.0.0",
    "platforms;android-34"
)
foreach ($pkg in $packages) {
    Write-Host "  Installing $pkg ..."
    & $SDKMANAGER $pkg --sdk_root="$SDK_DIR" 2>&1 | Out-Null
}
Write-Success "SDK components installed"

# ============================================
# Step 4: Download Gradle
# ============================================
Write-Host "`n[4/6] Setting up Gradle $GRADLE_VERSION..." -ForegroundColor Yellow
$GRADLE_URL = "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
$GRADLE_ZIP = "$env:TEMP\gradle-$GRADLE_VERSION-bin.zip"

if (-not (Test-Path "$GRADLE_DIR\bin\gradle.bat")) {
    Write-Host "  Downloading Gradle $GRADLE_VERSION ..."
    curl.exe -L -o $GRADLE_ZIP $GRADLE_URL
    
    if (-not (Test-Path "C:\Gradle")) {
        New-Item -ItemType Directory -Path "C:\Gradle" -Force | Out-Null
    }
    Expand-Archive -Path $GRADLE_ZIP -DestinationPath "C:\Gradle" -Force
    Remove-Item $GRADLE_ZIP -Force -ErrorAction SilentlyContinue
    
    Write-Success "Gradle $GRADLE_VERSION installed to $GRADLE_DIR"
} else {
    Write-Host "  Gradle already installed at $GRADLE_DIR" -ForegroundColor Gray
}

# ============================================
# Step 5: Set environment variables
# ============================================
Write-Host "`n[5/6] Setting environment variables..." -ForegroundColor Yellow

# ANDROID_HOME
$currentAndroidHome = [System.Environment]::GetEnvironmentVariable("ANDROID_HOME", "User")
if ($currentAndroidHome -ne $SDK_DIR) {
    [System.Environment]::SetEnvironmentVariable("ANDROID_HOME", $SDK_DIR, "User")
    $env:ANDROID_HOME = $SDK_DIR
    Write-Success "ANDROID_HOME = $SDK_DIR"
} else {
    Write-Host "  ANDROID_HOME already set" -ForegroundColor Gray
}

# Add to PATH
$userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
$pathsToAdd = @(
    "$SDK_DIR\platform-tools",
    "$SDK_DIR\cmdline-tools\latest\bin",
    "$GRADLE_DIR\bin"
)
$pathModified = $false
foreach ($p in $pathsToAdd) {
    if ($userPath -notlike "*$p*") {
        $userPath = "$userPath;$p"
        $pathModified = $true
    }
}
if ($pathModified) {
    [System.Environment]::SetEnvironmentVariable("Path", $userPath, "User")
    $env:Path = "$env:Path;$($pathsToAdd -join ';')"
    Write-Success "PATH updated"
}

# ============================================
# Step 6: Generate Gradle wrapper in project
# ============================================
Write-Host "`n[6/6] Generating Gradle wrapper in auto-clicker project..." -ForegroundColor Yellow
$GRADLE_EXE = "$GRADLE_DIR\bin\gradle.bat"

if (Test-Path $PROJECT_DIR) {
    Push-Location $PROJECT_DIR
    & $GRADLE_EXE wrapper --gradle-version $GRADLE_VERSION 2>&1 | Out-Null
    Pop-Location
    Write-Success "Gradle wrapper generated"
} else {
    Write-Host "  [WARN] Project directory not found: $PROJECT_DIR" -ForegroundColor Yellow
    Write-Host "  Wrapper will be generated after project creation." -ForegroundColor Yellow
}

# ============================================
# Create local.properties
# ============================================
$LOCAL_PROPS = Join-Path $PROJECT_DIR "local.properties"
if (Test-Path $PROJECT_DIR) {
    $sdkDirEscaped = $SDK_DIR.Replace('\', '\\')
    Set-Content -Path $LOCAL_PROPS -Value "sdk.dir=$sdkDirEscaped"
    Write-Success "local.properties created"
}

# ============================================
# Summary
# ============================================
Write-Host ""
Write-Header "SETUP COMPLETE"
Write-Host ""
Write-Host "  ANDROID_HOME : $SDK_DIR" -ForegroundColor Green
Write-Host "  Gradle       : $GRADLE_DIR" -ForegroundColor Green
Write-Host "  ADB          : $(Get-AdbPath)" -ForegroundColor Green
Write-Host ""
Write-Host "  Next steps:" -ForegroundColor Yellow
Write-Host "    1. Restart PowerShell to load new PATH" -ForegroundColor Yellow
Write-Host "    2. cd auto-clicker" -ForegroundColor Yellow
Write-Host "    3. .\gradlew.bat assembleDebug" -ForegroundColor Yellow
Write-Host ""
