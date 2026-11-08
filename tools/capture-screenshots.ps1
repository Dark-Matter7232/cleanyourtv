# Capture one or more screenshots from an Android TV and pull them into a local folder.
#
# Usage:
#   capture-tv-screenshot.bat
#   tools\capture-screenshots.ps1 -TvHost <tv ip>
#   tools\capture-screenshots.ps1 -TvHost <tv ip> -Count 5 -IntervalSeconds 10
#   tools\capture-screenshots.ps1 -TvHost <tv ip> -Keys HOME,DPAD_RIGHT
#
# The wrapper uses tools\tv.py, so it supports both ordinary network ADB and the optional
# TV_ADB_BINARY backend for paired Wireless debugging. It changes nothing on the TV beyond
# the temporary screencap file that tv.py removes after each pull.
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$TvHost = $env:TV_HOST,

    [string]$TvPort = $env:TV_PORT,

    [string]$OutputDir,

    [ValidateRange(1, 1000)]
    [int]$Count = 1,

    [ValidateRange(0, 3600)]
    [double]$IntervalSeconds = 5,

    [ValidateRange(0, 300)]
    [double]$WaitSeconds = 1,

    [string[]]$Keys = @(),

    [ValidateNotNullOrEmpty()]
    [string]$Prefix = 'tv-screenshot'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$tvPy = Join-Path $repo 'tools\tv.py'
$venvPython = Join-Path $repo 'tools\venv\Scripts\python.exe'

if (-not (Test-Path -LiteralPath $tvPy -PathType Leaf)) {
    throw "Cannot find tools\tv.py beside this script: $tvPy"
}

if ([string]::IsNullOrWhiteSpace($TvHost)) {
    $TvHost = Read-Host 'TV IP address (or IP:port for Wireless debugging)'
}
if ([string]::IsNullOrWhiteSpace($TvHost)) {
    throw 'A TV address is required. Pass -TvHost <tv ip> or set TV_HOST.'
}

if ($Prefix -match '[\\/:*?"<>|]' -or $Prefix -match '^\s|\s$') {
    throw "Prefix must be a simple filename prefix without path separators: $Prefix"
}

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repo 'shots'
} else {
    $OutputDir = [IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputDir))
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$python = $null
$pythonArgs = @()
if (Test-Path -LiteralPath $venvPython -PathType Leaf) {
    $python = $venvPython
} else {
    $command = Get-Command python -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        $command = Get-Command py -ErrorAction SilentlyContinue
        if ($null -ne $command) {
            $python = $command.Source
            $pythonArgs = @('-3')
        }
    } else {
        $python = $command.Source
    }
}
if (-not $python) {
    throw 'Python 3 was not found. Install Python or create tools\venv with tools\requirements.txt.'
}

$env:TV_HOST = $TvHost
if (-not [string]::IsNullOrWhiteSpace($TvPort)) {
    $env:TV_PORT = $TvPort
}

$invariant = [Globalization.CultureInfo]::InvariantCulture
$waitText = $WaitSeconds.ToString($invariant)
$intervalText = $IntervalSeconds.ToString($invariant)
Write-Host "Saving screenshots to $OutputDir"
Write-Host "Connecting to $TvHost$(if ($TvPort) { ":$TvPort" } else { '' })"

for ($number = 1; $number -le $Count; $number++) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $name = '{0}-{1}-{2:D3}.png' -f $Prefix, $stamp, $number
    $destination = Join-Path $OutputDir $name

    $tvArgs = @('-I', $tvPy, '--screenshot', $destination)
    if ($Keys.Count -gt 0) {
        $tvArgs += $Keys
    }
    $tvArgs += @('--wait', $waitText)

    Write-Host "[$number/$Count] Capturing $name"
    $invokeArgs = @($pythonArgs + $tvArgs)
    & $python @invokeArgs
    $status = $LASTEXITCODE
    if ($status -ne 0) {
        [Console]::Error.WriteLine("Screenshot capture failed with exit code $status. The previous captures, if any, are kept in $OutputDir.")
        exit $status
    }

    if ($number -lt $Count -and $IntervalSeconds -gt 0) {
        Start-Sleep -Seconds $IntervalSeconds
    }
}

Write-Host "Done: $Count screenshot$(if ($Count -eq 1) { '' } else { 's' }) saved in $OutputDir"
