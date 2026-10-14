<#
.SYNOPSIS
    Stops the CareerPilot apps started by start-local.ps1. With -All, also stops MySQL, Redis and RabbitMQ.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1 -All
#>
param(
    [switch]$All,
    [string]$Runtime = (Join-Path $env:USERPROFILE 'placify-runtime')
)

$appPorts = 3000, 8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089, 8090, 8091, 8761
$owners = Get-NetTCPConnection -State Listen -LocalPort $appPorts -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
foreach ($id in $owners) { Stop-Process -Id $id -Force -ErrorAction SilentlyContinue }
Write-Host "Stopped $(@($owners).Count) CareerPilot app(s)."
# public tunnels opened by share-online.ps1
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

if ($All) {
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
    Write-Host 'Stopped MySQL, Redis and RabbitMQ.'
}
