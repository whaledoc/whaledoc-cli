# WhaleDoc CLI installer for Windows.
#
#   irm https://whaledoc.io/install.ps1 | iex
#
# Environment variables:
#   WHALEDOC_VERSION      Version to install, e.g. 1.2.0 (default: latest)
#   WHALEDOC_INSTALL_DIR  Install directory (default: %LOCALAPPDATA%\Programs\whaledoc)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# Windows PowerShell 5.1 does not enable TLS 1.2 by default
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$Repo = 'whaledoc/whaledoc-cli'
$Version = if ($env:WHALEDOC_VERSION) { $env:WHALEDOC_VERSION } else { 'latest' }
$InstallDir = if ($env:WHALEDOC_INSTALL_DIR) { $env:WHALEDOC_INSTALL_DIR } else { Join-Path $env:LOCALAPPDATA 'Programs\whaledoc' }

# Only an x64 build is published; Windows on ARM runs it under emulation
$Archive = 'whaledoc-windows-x64.zip'

if ($Version -eq 'latest') {
    $BaseUrl = "https://github.com/$Repo/releases/latest/download"
} else {
    $BaseUrl = "https://github.com/$Repo/releases/download/v$($Version.TrimStart('v'))"
}

$Temp = Join-Path ([IO.Path]::GetTempPath()) ([Guid]::NewGuid())
New-Item -ItemType Directory -Path $Temp | Out-Null

try {
    Write-Host "Downloading WhaleDoc CLI ($Version, windows-x64)..."
    Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/$Archive" -OutFile (Join-Path $Temp $Archive)
    Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/checksums.txt" -OutFile (Join-Path $Temp 'checksums.txt')

    $Line = Get-Content (Join-Path $Temp 'checksums.txt') | Where-Object { $_ -match "\s$([regex]::Escape($Archive))$" }
    if (-not $Line) { throw "No checksum found for $Archive" }
    $Expected = ($Line -split '\s+')[0]
    $Actual = (Get-FileHash -Algorithm SHA256 (Join-Path $Temp $Archive)).Hash
    if ($Actual -ne $Expected) { throw "Checksum mismatch for $Archive" }

    Expand-Archive -Path (Join-Path $Temp $Archive) -DestinationPath $Temp -Force
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
    Move-Item -Path (Join-Path $Temp 'whaledoc.exe') -Destination (Join-Path $InstallDir 'whaledoc.exe') -Force
} finally {
    Remove-Item -Recurse -Force $Temp -ErrorAction SilentlyContinue
}

$UserPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if (-not (($UserPath -split ';') -contains $InstallDir)) {
    $NewPath = if ($UserPath) { "$UserPath;$InstallDir" } else { $InstallDir }
    [Environment]::SetEnvironmentVariable('Path', $NewPath, 'User')
    Write-Host "Added $InstallDir to your user PATH. Restart your terminal for it to take effect."
}
$env:Path = "$env:Path;$InstallDir"

Write-Host "Installed $(& (Join-Path $InstallDir 'whaledoc.exe') --version) to $InstallDir\whaledoc.exe"
Write-Host "Run 'whaledoc --help' to get started."
