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
    [int]$CheckSeconds = 60,
    [string]$VercelDomain = 'careerpilot-portal.vercel.app'
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

$log = Join-Path $env:USERPROFILE 'placify-runtime\logs\keep-online.log'
function Write-Log([string]$Text) {
    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')  $Text"
    Write-Host $line
    Add-Content -Path $log -Value $line -ErrorAction SilentlyContinue
}

# Reopens the tunnels; returns $false (instead of stopping the watcher) when it fails, e.g. while offline.
function Share {
    try {
        & (Join-Path $PSScriptRoot 'share-online.ps1') *>&1 | ForEach-Object { Write-Log "  $_" }
        return Test-Portal (Get-PortalUrl)
    } catch {
        Write-Log "  could not reopen the tunnels: $($_.Exception.Message)"
        return $false
    }
}

# The address the published Vercel page currently forwards to ($null when Vercel cannot be reached).
function Get-VercelTarget {
    try {
        $r = Invoke-WebRequest "https://$VercelDomain/?check=$(Get-Random)" -UseBasicParsing -TimeoutSec 20 -Headers @{ 'Cache-Control' = 'no-cache' }
        return [regex]::Match($r.Content, "const APP_URL = '([^']*)';").Groups[1].Value
    } catch { return $null }
}

# Re-deploys the Vercel page when it still forwards to an old tunnel (e.g. after a failed deploy).
function Sync-Vercel {
    $live = Get-VercelTarget
    $want = Get-PortalUrl
    if ($null -eq $live -or $live -eq $want) { return }
    Write-Log "Vercel page points to $live, updating it to $want ..."
    Push-Location (Split-Path $page)
    try {
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            $out = (& vercel deploy --prod --yes 2>&1 | Out-String)
            if ($LASTEXITCODE -eq 0) { Write-Log 'Vercel page updated'; return }
            Write-Log "  Vercel update failed (attempt $attempt of 3): $(($out -split "`n" | Select-String 'Error' | Select-Object -First 1))"
            Start-Sleep -Seconds 15
        }
    } catch {
        Write-Log "  Vercel update failed: $($_.Exception.Message)"
    } finally {
        Pop-Location
    }
}

try {
    Write-Log 'keep-online started'
    if (-not (Test-Portal (Get-PortalUrl))) { [void](Share) }
    Sync-Vercel
    Write-Log "Watching the public link every $CheckSeconds s. Share: https://careerpilot-portal.vercel.app"
    $failures = 0
    while ($true) {
        Start-Sleep -Seconds $CheckSeconds
        if (Test-Portal (Get-PortalUrl)) {
            if ($failures -gt 0) { Write-Log 'public link is answering again' }
            $failures = 0
            Sync-Vercel
            continue
        }
        $failures++
        Write-Log "public link not answering ($failures/3)"
        if ($failures -ge 3) {
            Write-Log 'reopening the tunnels ...'
            if (Share) {
                Write-Log "online again: $(Get-PortalUrl)"
                $failures = 0
            } else {
                $failures = 2   # try again after the next failed check (e.g. once the internet is back)
            }
        }
    }
} finally {
    Write-Log 'keep-online stopped'
    [void][Win32.Power]::SetThreadExecutionState($ES_CONTINUOUS)
}
