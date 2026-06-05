# sync-to-phone.ps1
# Script to sync this project directory to the phone via SFTP using the 'phone' SSH host alias.

$SshHost = "phone"
$RemoteBaseDir = "temux-install"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  STARTING SYNC TO PHONE VIA SFTP" -ForegroundColor Cyan
Write-Host "  Remote Target: ${SshHost}:${RemoteBaseDir}" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 1. Get current path
$LocalPath = (Get-Item .).FullName

# 2. Get files and folders to sync (exclude git, dependencies, and IDE configuration files)
$Files = Get-ChildItem -Path $LocalPath -Recurse -File | Where-Object {
    $_.FullName -notmatch '\\\.git($|\\)' -and
    $_.FullName -notmatch '\\node_modules($|\\)' -and
    $_.FullName -notmatch '\\\.venv($|\\)' -and
    $_.FullName -notmatch '\\venv($|\\)' -and
    $_.FullName -notmatch '\\__pycache__($|\\)' -and
    $_.FullName -notmatch '\\\.vscode($|\\)' -and
    $_.Name -ne "sync-to-phone.ps1" -and
    $_.Name -ne ".gitignore"
}

$Dirs = Get-ChildItem -Path $LocalPath -Recurse -Directory | Where-Object {
    $_.FullName -notmatch '\\\.git($|\\)' -and
    $_.FullName -notmatch '\\node_modules($|\\)' -and
    $_.FullName -notmatch '\\\.venv($|\\)' -and
    $_.FullName -notmatch '\\venv($|\\)' -and
    $_.FullName -notmatch '\\__pycache__($|\\)' -and
    $_.FullName -notmatch '\\\.vscode($|\\)'
}

# 3. Convert line endings of synced files to Unix (LF) format locally
Write-Host "Converting line endings to Unix format (LF)..." -ForegroundColor Yellow
foreach ($File in $Files) {
    if ($File.Extension -in @(".sh", ".py", ".md", ".json", ".txt")) {
        $Text = [System.IO.File]::ReadAllText($File.FullName)
        if ($Text -match "`r`n") {
            $LfText = $Text.Replace("`r`n", "`n")
            [System.IO.File]::WriteAllText($File.FullName, $LfText)
        }
    }
}

# 4. Build SFTP batch commands
$BatchFile = [System.IO.Path]::GetTempFileName()
$BatchContent = [System.Collections.Generic.List[string]]::new()

# Ensure base directory exists (dash prefix ignores failures if it exists)
$BatchContent.Add("-mkdir $RemoteBaseDir")

# Ensure subdirectories exist
foreach ($Dir in $Dirs) {
    $RelativePath = $Dir.FullName.Substring($LocalPath.Length + 1).Replace('\', '/')
    $RemoteDirPath = "$RemoteBaseDir/$RelativePath"
    $BatchContent.Add("-mkdir `"$RemoteDirPath`"")
}

# Add put commands for all files
foreach ($File in $Files) {
    $RelativePath = $File.FullName.Substring($LocalPath.Length + 1).Replace('\', '/')
    $RemoteFilePath = "$RemoteBaseDir/$RelativePath"
    $LocalFilePath = $File.FullName
    $BatchContent.Add("put `"$LocalFilePath`" `"$RemoteFilePath`"")
}

# Quit command
$BatchContent.Add("quit")

# Save batch script
[System.IO.File]::WriteAllLines($BatchFile, $BatchContent)

# 5. Run sftp command
Write-Host "Syncing files..." -ForegroundColor Yellow
& sftp -b $BatchFile $SshHost

# Clean up
if (Test-Path $BatchFile) {
    Remove-Item $BatchFile -Force
}

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  SYNC COMPLETE!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
