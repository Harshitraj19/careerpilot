<#
.SYNOPSIS
    Puts the CareerPilot running on this laptop online with a public https link, and points the Vercel front page at it.

.DESCRIPTION
    Opens two Cloudflare quick tunnels (no account needed): one for the portal (port 3000) and one for the gateway
    (port 8080, used by live notifications). Restarts the gateway, notification service and web UI with the new
    public addresses, writes the portal address into deploy\vercel\index.html and redeploys it with the Vercel CLI.
    The links work while this laptop is on and CareerPilot is running; they change every time this script runs.
    Stop everything with: powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1

.PARAMETER NoVercel
    Only open the tunnels; do not redeploy the Vercel page.

.PARAMETER VercelDomain
    The address of the Vercel page (a domain assigned to the Vercel project "careerpilot").

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\share-online.ps1
#>
param(
    [switch]$NoVercel,
    [string]$VercelDomain = 'careerpilot-portal.vercel.app',
    [string]$Runtime = (Join-Path $env:USERPROFILE 'placify-runtime')
)

$ErrorActionPreference = 'Continue'
$Project = Split-Path -Parent $PSScriptRoot
$Logs = Join-Path $Runtime 'logs'
$cloudflared = Join-Path $Runtime 'cloudflared\cloudflared.exe'
New-Item -ItemType Directory -Force $Logs, (Split-Path $cloudflared) | Out-Null

if (-not (Test-Path $cloudflared)) {
    Write-Host 'Downloading cloudflared ...'
    Invoke-WebRequest 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe' -OutFile $cloudflared -UseBasicParsing
}

function Open-Tunnel([int]$Port, [string]$Name) {
    $log = Join-Path $Logs "tunnel-$Name.log"
    Remove-Item $log -ErrorAction SilentlyContinue
    Start-Process $cloudflared -ArgumentList "tunnel --no-autoupdate --url http://localhost:$Port --logfile `"$log`"" -WindowStyle Hidden
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 1
        if (Test-Path $log) {
            $m = Select-String -Path $log -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
            if ($m) { return $m.Matches[0].Value }
        }
    }
    throw "The $Name tunnel did not start. See $log"
}

# ------------------------------------------------------------------ tunnels
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force
Write-Host 'Opening the public tunnels ...'
$appUrl = Open-Tunnel 3000 'portal'
$gatewayUrl = Open-Tunnel 8080 'gateway'
$wsUrl = ($gatewayUrl -replace '^https://', 'wss://') + '/ws/notifications'

# ------------------------------------------------------------------ restart the apps that need the public addresses
foreach ($port in 3000, 8080, 8090) {
    Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }
}
& (Join-Path $PSScriptRoot 'start-local.ps1') -PublicUrl $appUrl -PublicWsUrl $wsUrl -Runtime $Runtime

# ------------------------------------------------------------------ Vercel front page
$page = Join-Path $Project 'deploy\vercel\index.html'
$html = [IO.File]::ReadAllText($page)
$html = [regex]::Replace($html, "const APP_URL = '[^']*';", "const APP_URL = '$appUrl';")
[IO.File]::WriteAllText($page, $html, (New-Object System.Text.UTF8Encoding $false))
$vercelLink = $null
if (-not $NoVercel -and (Get-Command vercel -ErrorAction SilentlyContinue)) {
    Write-Host 'Updating the Vercel page ...'
    Push-Location (Split-Path $page)
    # Vercel occasionally rejects a deploy ("Not authorized") while it refreshes the login; try a few times
    for ($attempt = 1; $attempt -le 3 -and -not $vercelLink; $attempt++) {
        $out = (& vercel deploy --prod --yes 2>&1 | Out-String)
        if ($LASTEXITCODE -eq 0) {
            $vercelLink = "https://$VercelDomain"
        } else {
            Write-Host "Vercel update failed (attempt $attempt of 3): $(($out -split "`n" | Select-String 'Error' | Select-Object -First 1))"
            Start-Sleep -Seconds 15
        }
    }
    Pop-Location
}

Write-Host ''
Write-Host "CareerPilot is online:   $appUrl"
if ($vercelLink) { Write-Host "Vercel page:         $vercelLink  (opens the portal above)" }
Write-Host 'The links work while this laptop is on and CareerPilot is running.'
