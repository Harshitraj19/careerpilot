<#
.SYNOPSIS
    Stops everything CareerPilot started: the 14 apps, MySQL, Redis, RabbitMQ, the public tunnels and the
    Gradle build daemons, so no memory stays in use after you are done.

.PARAMETER KeepInfra
    Leave MySQL, Redis and RabbitMQ running (faster next start, but they keep about 1 GB of RAM in use).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1
#>
param(
    [switch]$KeepInfra,
    [switch]$All,   # older name for the default behaviour, still accepted
    [string]$Runtime = (Join-Path $env:USERPROFILE 'placify-runtime')
)

$Project = Split-Path -Parent $PSScriptRoot

$appPorts = 3000, 8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089, 8090, 8091, 8761
$owners = Get-NetTCPConnection -State Listen -LocalPort $appPorts -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
foreach ($id in $owners) { Stop-Process -Id $id -Force -ErrorAction SilentlyContinue }
Write-Host "Stopped $(@($owners).Count) CareerPilot app(s)."

# the keep-online watcher (it would reopen the tunnels) and the public tunnels opened by share-online.ps1
Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*keep-online.ps1*' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

# Gradle keeps build daemons (300-400 MB each) alive for hours after a build
Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*GradleDaemon*' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

if (-not $KeepInfra) {
    $mysqladmin = Join-Path $Runtime 'mysql\bin\mysqladmin.exe'
    if (Test-Path $mysqladmin) { & $mysqladmin -uroot shutdown 2>$null }
    $rabbitctl = Join-Path $Runtime 'rabbitmq\sbin\rabbitmqctl.bat'
    if (Test-Path $rabbitctl) {
        $env:ERLANG_HOME = Join-Path $Runtime 'erlang'
        $env:RABBITMQ_BASE = Join-Path $Runtime 'rabbitmq-data'
        & $rabbitctl -n rabbit@localhost shutdown 2>$null | Out-Null
    }
    $infra = Get-NetTCPConnection -State Listen -LocalPort 3306, 6379, 5672 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($id in $infra) { Stop-Process -Id $id -Force -ErrorAction SilentlyContinue }
    # Erlang's port mapper, started with RabbitMQ from the portable runtime
    Get-Process epmd, erl, erlsrv -ErrorAction SilentlyContinue |
        Where-Object { $_.Path -like "$Runtime*" } | Stop-Process -Force -ErrorAction SilentlyContinue
    Write-Host 'Stopped MySQL, Redis and RabbitMQ.'
}
Write-Host 'CareerPilot is fully stopped.'
