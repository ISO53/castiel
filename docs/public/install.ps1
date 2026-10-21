# castiel installer for Windows (PowerShell 5.1+).
#
#   irm https://iso53.github.io/castiel/install.ps1 | iex
#
# Downloads the latest windows-x64 release bundle, installs the self-contained
# app image into LOCALAPPDATA and adds the launcher to the user PATH.
$ErrorActionPreference = "Stop"

$Repo = "ISO53/castiel"
$Asset = "castiel-windows-x64.zip"
$Base = "https://github.com/$Repo/releases/latest/download/$Asset"
$Dest = Join-Path $env:LOCALAPPDATA "Programs\castiel"

# TLS 1.2 is not always the default in Windows PowerShell 5.1.
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

Write-Host "Downloading castiel (latest release)..." -ForegroundColor White

$tmpZip = Join-Path ([System.IO.Path]::GetTempPath()) "castiel-install.zip"
$tmpDir = Join-Path ([System.IO.Path]::GetTempPath()) "castiel-install"
Remove-Item $tmpZip, $tmpDir -Recurse -Force -ErrorAction SilentlyContinue

try {
    Invoke-WebRequest -Uri $Base -OutFile $tmpZip -UseBasicParsing
} catch {
    Write-Host "error: download failed — check https://github.com/$Repo/releases" -ForegroundColor Red
    exit 1
}
Expand-Archive -Path $tmpZip -DestinationPath $tmpDir -Force

$src = Join-Path $tmpDir "castiel"
if (-not (Test-Path (Join-Path $src "castiel.exe"))) {
    Write-Host "error: unexpected archive layout — check https://github.com/$Repo/releases" -ForegroundColor Red
    exit 1
}

if (Test-Path $Dest) { Remove-Item $Dest -Recurse -Force }
New-Item -ItemType Directory -Path (Split-Path $Dest) -Force | Out-Null
Move-Item $src $Dest
Remove-Item $tmpZip, $tmpDir -Recurse -Force -ErrorAction SilentlyContinue

$binDir = $Dest  # castiel.exe sits at the image root
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if (($userPath -split ";") -notcontains $binDir) {
    [Environment]::SetEnvironmentVariable("Path", "$userPath;$binDir", "User")
    Write-Host "Added $binDir to your user PATH." -ForegroundColor White
}

Write-Host ""
Write-Host "Installed castiel to $Dest" -ForegroundColor Green
Write-Host "Restart your terminal, then run: castiel" -ForegroundColor White
