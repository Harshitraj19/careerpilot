<#
.SYNOPSIS
    Keeps the public CareerPilot link working during a demo.

.DESCRIPTION
    Runs share-online.ps1, then checks the public portal address every minute. Free Cloudflare quick tunnels are
    deleted when the laptop sleeps or the network drops; when the address stops answering, this script opens new
    tunnels and updates the Vercel page again, so https://careerpilot-portal.vercel.app keeps working.
    It also stops Windows from going to sleep while it runs. Leave the window open; press Ctrl+C to stop.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\keep-online.ps1
#>
param(
    [int]$CheckSeconds = 60
)

$ErrorActionPreference = 'Continue'
$Project = Split-Path -Parent $PSScriptRoot
$page = Join-Path $Project 'deploy\vercel\index.html'

# keep the system (not the display) awake while this script runs
Add-Type -Namespace Win32 -Name Power -MemberDefinition @'
[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint esFlags);
'@
$ES_CONTINUOUS = [uint32]'0x80000000'; $ES_SYSTEM_REQUIRED = [uint32]'0x00000001'
[void][Win32.Power]::SetThreadExecutionState($ES_CONTINUOUS -bor $ES_SYSTEM_REQUIRED)

function Get-PortalUrl {
    $m = [regex]::Match([IO.File]::ReadAllText($page), "const APP_URL = '([^']*)';")
    return $m.Groups[1].Value
}

function Test-Portal([string]$Url) {
    if (-not $Url) { return $false }
    try {
        $r = Invoke-WebRequest "$Url/login" -UseBasicParsing -TimeoutSec 20
        return $r.StatusCode -eq 200
    } catch { return $false }
}

function Share {
    & (Join-Path $PSScriptRoot 'share-online.ps1')
}

try {
    if (-not (Test-Portal (Get-PortalUrl))) { Share }
    Write-Host ''
    Write-Host "Watching the public link (every $CheckSeconds s). Share: https://careerpilot-portal.vercel.app"
    $failures = 0
    while ($true) {
        Start-Sleep -Seconds $CheckSeconds
        if (Test-Portal (Get-PortalUrl)) {
            $failures = 0
            continue
        }
        $failures++
        Write-Host "$(Get-Date -Format 'HH:mm:ss')  public link not answering ($failures/3)"
        if ($failures -ge 3) {
            Write-Host 'Reopening the tunnels ...'
            Share
            $failures = 0
        }
    }
} finally {
    [void][Win32.Power]::SetThreadExecutionState($ES_CONTINUOUS)
}
